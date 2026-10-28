import { HttpStatus, Injectable, Logger } from '@nestjs/common';
import { Prisma, QuestDefinition, UserQuest } from '@prisma/client';
import { ApiError, badRequest, notFound } from '../common/errors';
import { dayKey, endOfDay, endOfWeek, weekKey } from '../common/periods';
import { PrismaService, Tx } from '../prisma/prisma.service';
import { AchievementsService } from './achievements.service';
import { ChallengesService } from './challenges.service';
import { emptyEffects, GameEffects } from './effects';
import { applyEvent, GameEvent, progressUnit, QuestConditionName, QuestDef } from './quest-engine';
import { XpService } from './xp.service';
import { EffectsPublisher } from './effects-publisher.service';

/** Completed quests stay claimable this long after their period ends. */
export const CLAIM_GRACE_MS = 3 * 86_400_000;

@Injectable()
export class QuestsService {
  private readonly log = new Logger('Quests');

  constructor(
    private readonly prisma: PrismaService,
    private readonly xp: XpService,
    private readonly achievements: AchievementsService,
    private readonly challenges: ChallengesService,
    private readonly publisher: EffectsPublisher,
  ) {}

  private period(def: QuestDefinition, now: Date): { key: string; expiresAt: Date } | null {
    if (def.startsAt && def.startsAt > now) return null;
    if (def.endsAt && def.endsAt <= now) return null;
    switch (def.type) {
      case 'DAILY':
        return { key: dayKey(now), expiresAt: def.endsAt && def.endsAt < endOfDay(now) ? def.endsAt : endOfDay(now) };
      case 'WEEKLY':
        return { key: weekKey(now), expiresAt: def.endsAt && def.endsAt < endOfWeek(now) ? def.endsAt : endOfWeek(now) };
      case 'SPECIAL':
        if (!def.endsAt) return null; // special events must have an end
        return { key: `event:${(def.startsAt ?? def.createdAt).toISOString().slice(0, 10)}`, expiresAt: def.endsAt };
    }
  }

  /** Creates this period's quest rows for the player. Idempotent. */
  async ensureQuests(userId: string, now = new Date()) {
    const profile = await this.prisma.userProfile.findUniqueOrThrow({ where: { userId } });
    const defs = await this.prisma.questDefinition.findMany({ where: { active: true, minLevel: { lte: profile.level } } });
    const rows: Prisma.UserQuestCreateManyInput[] = [];
    for (const d of defs) {
      const p = this.period(d, now);
      if (!p) continue;
      // Group objectives need a team circle; without one the quest is simply not offered.
      if (d.condition === 'CIRCLE_TILES' && !profile.teamCircleId) continue;
      rows.push({
        userId,
        questId: d.id,
        periodKey: p.key,
        expiresAt: p.expiresAt,
        circleId: d.condition === 'CIRCLE_TILES' ? profile.teamCircleId : null,
      });
    }
    if (rows.length) await this.prisma.userQuest.createMany({ data: rows, skipDuplicates: true });
    await this.prisma.userQuest.updateMany({ where: { userId, status: 'ACTIVE', expiresAt: { lte: now } }, data: { status: 'EXPIRED' } });
    await this.prisma.userQuest.updateMany({
      where: { userId, status: 'COMPLETED', expiresAt: { lte: new Date(now.getTime() - CLAIM_GRACE_MS) } },
      data: { status: 'EXPIRED' },
    });
  }

  view(q: UserQuest & { quest: QuestDefinition }) {
    return {
      id: q.id,
      questCode: q.quest.code,
      name: q.quest.name,
      description: q.quest.description,
      type: q.quest.type,
      condition: q.quest.condition,
      unit: progressUnit(q.quest.condition as QuestConditionName),
      target: q.quest.target,
      progress: q.progress,
      status: q.status,
      rewardXp: q.quest.rewardXp,
      rewardCollectibleCode: q.quest.rewardCollectibleCode,
      circleId: q.circleId,
      startedAt: q.startedAt,
      expiresAt: q.expiresAt,
      completedAt: q.completedAt,
      claimedAt: q.claimedAt,
      params: q.quest.params,
    };
  }

  async list(userId: string) {
    await this.ensureQuests(userId);
    const rows = await this.prisma.userQuest.findMany({
      where: { userId, OR: [{ status: { in: ['ACTIVE', 'COMPLETED'] } }, { status: 'CLAIMED', claimedAt: { gt: new Date(Date.now() - 7 * 86_400_000) } }] },
      include: { quest: true },
      orderBy: [{ expiresAt: 'asc' }],
    });
    return rows.map((r) => this.view(r));
  }

  async history(userId: string, cursor?: string, limit = 30) {
    const rows = await this.prisma.userQuest.findMany({
      where: { userId, status: { in: ['CLAIMED', 'EXPIRED'] }, ...(cursor ? { startedAt: { lt: new Date(cursor) } } : {}) },
      include: { quest: true },
      orderBy: { startedAt: 'desc' },
      take: limit + 1,
    });
    const page = rows.slice(0, limit);
    return { items: page.map((r) => this.view(r)), nextCursor: rows.length > limit ? page[page.length - 1].startedAt.toISOString() : null };
  }

  async get(userId: string, userQuestId: string) {
    const q = await this.prisma.userQuest.findFirst({ where: { id: userQuestId, userId }, include: { quest: true } });
    if (!q) throw notFound('Quest');
    const log = await this.prisma.questProgress.findMany({ where: { userQuestId }, orderBy: { createdAt: 'desc' }, take: 20 });
    return { ...this.view(q), recentProgress: log.map((l) => ({ delta: l.delta, reason: l.reason, at: l.createdAt })) };
  }

  /**
   * Feeds game events into the player's open quests (and, for circle tiles, into every
   * circle-mate's group quest). Rows are locked so concurrent uploads cannot lose progress.
   */
  async applyEvents(tx: Tx, userId: string, events: GameEvent[], fx: GameEffects) {
    if (events.length === 0) return;
    const now = new Date();
    const circleTiles = events.filter((e): e is Extract<GameEvent, { kind: 'CIRCLE_TILE' }> => e.kind === 'CIRCLE_TILE');
    const circleIds = [...new Set(circleTiles.map((e) => e.circleId))];
    const where: Prisma.UserQuestWhereInput = {
      status: 'ACTIVE',
      expiresAt: { gt: now },
      OR: [{ userId }, ...(circleIds.length ? [{ circleId: { in: circleIds }, quest: { condition: 'CIRCLE_TILES' as const } }] : [])],
    };
    const ids = (await tx.userQuest.findMany({ where, select: { id: true } })).map((r) => r.id);
    if (ids.length === 0) return;
    await tx.$queryRaw`SELECT "id" FROM "UserQuest" WHERE "id" = ANY(${ids}::uuid[]) ORDER BY "id" FOR UPDATE`;
    const quests = await tx.userQuest.findMany({ where: { id: { in: ids } }, include: { quest: true } });

    for (const q of quests) {
      if (q.userId !== userId && q.quest.condition !== 'CIRCLE_TILES') continue;
      const def: QuestDef = { condition: q.quest.condition as QuestConditionName, target: q.quest.target, params: q.quest.params as QuestDef['params'] };
      let state = { progress: q.progress, progressKeys: q.progressKeys, circleId: q.circleId };
      let delta = 0;
      let completed = false;
      for (const e of events) {
        // A member's own TILE events are not circle events for other people's quests.
        if (q.userId !== userId && e.kind !== 'CIRCLE_TILE') continue;
        const u = applyEvent(def, state, e);
        if (!u) continue;
        state = { ...state, progress: u.progress, progressKeys: u.progressKeys };
        delta += u.delta;
        completed = completed || u.completed;
      }
      if (delta === 0) continue;
      await tx.userQuest.update({
        where: { id: q.id },
        data: { progress: state.progress, progressKeys: state.progressKeys, ...(completed ? { status: 'COMPLETED', completedAt: now } : {}) },
      });
      await tx.questProgress.create({ data: { userQuestId: q.id, delta, reason: events.map((e) => e.kind).join(',').slice(0, 120) } });
      fx.questProgress.set(q.id, { userId: q.userId, userQuestId: q.id, questName: q.quest.name, progress: state.progress, target: q.quest.target, completed });
    }
  }

  /**
   * Claims a completed quest's reward. The Idempotency-Key makes retries safe: the same key
   * returns the original result; a different key for an already claimed quest returns it too
   * with alreadyClaimed=true. XP is granted once either way.
   */
  async claim(userId: string, userQuestId: string, idempotencyKey: string) {
    if (!/^[A-Za-z0-9_-]{8,64}$/.test(idempotencyKey)) throw badRequest('INVALID_IDEMPOTENCY_KEY', 'Send an Idempotency-Key header (8-64 url-safe characters)');
    const key = `${userId}:${idempotencyKey}`;
    const prior = await this.prisma.questCompletion.findUnique({ where: { idempotencyKey: key } });
    if (prior) {
      if (prior.userQuestId !== userQuestId) throw new ApiError(HttpStatus.UNPROCESSABLE_ENTITY, 'IDEMPOTENCY_KEY_REUSED', 'This key was already used for another claim');
      return this.claimResult(userId, prior.userQuestId, prior.xpAwarded, true, emptyEffects());
    }
    const fx = emptyEffects();
    try {
      const xp = await this.prisma.$transaction(async (tx) => {
        await tx.$queryRaw`SELECT "id" FROM "UserQuest" WHERE "id" = ${userQuestId}::uuid FOR UPDATE`;
        const q = await tx.userQuest.findFirst({ where: { id: userQuestId, userId }, include: { quest: true } });
        if (!q) throw notFound('Quest');
        if (q.status === 'CLAIMED') return null;
        if (q.status !== 'COMPLETED') throw new ApiError(HttpStatus.CONFLICT, 'QUEST_NOT_COMPLETE', 'Finish the quest before claiming it');
        if (q.expiresAt.getTime() + CLAIM_GRACE_MS <= Date.now()) throw new ApiError(HttpStatus.CONFLICT, 'QUEST_EXPIRED', 'The claim window for this quest has closed');
        await tx.questCompletion.create({ data: { userQuestId: q.id, userId, questId: q.questId, idempotencyKey: key, xpAwarded: q.quest.rewardXp } });
        await tx.userQuest.update({ where: { id: q.id }, data: { status: 'CLAIMED', claimedAt: new Date() } });
        await tx.userProfile.update({ where: { userId }, data: { questsCompleted: { increment: 1 } } });
        await this.xp.grant(tx, userId, q.quest.rewardXp, 'QUEST', q.id, fx);
        if (q.quest.rewardCollectibleCode) await this.xp.grantCollectible(tx, userId, q.quest.rewardCollectibleCode, `quest:${q.quest.code}`, fx);
        await this.challenges.bump(tx, userId, 'QUESTS', 1, fx);
        await this.achievements.evaluate(tx, userId, fx);
        return q.quest.rewardXp;
      });
      if (xp === null) {
        const done = await this.prisma.questCompletion.findUniqueOrThrow({ where: { userQuestId } });
        return this.claimResult(userId, userQuestId, done.xpAwarded, true, fx);
      }
      await this.publisher.publish(userId, fx);
      return this.claimResult(userId, userQuestId, xp, false, fx);
    } catch (e) {
      // Lost a race with an identical request: report what the winner did.
      if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2002') {
        const done = await this.prisma.questCompletion.findUnique({ where: { userQuestId } });
        if (done) return this.claimResult(userId, userQuestId, done.xpAwarded, true, emptyEffects());
      }
      throw e;
    }
  }

  private async claimResult(userId: string, userQuestId: string, xpAwarded: number, alreadyClaimed: boolean, fx: GameEffects) {
    const q = await this.prisma.userQuest.findUniqueOrThrow({ where: { id: userQuestId }, include: { quest: true } });
    const profile = await this.prisma.userProfile.findUniqueOrThrow({ where: { userId } });
    return {
      quest: this.view(q),
      xpAwarded,
      alreadyClaimed,
      totalXp: profile.xp,
      level: profile.level,
      levelUps: fx.levelUps,
      achievements: fx.achievements,
      collectibles: fx.collectibles,
    };
  }
}
