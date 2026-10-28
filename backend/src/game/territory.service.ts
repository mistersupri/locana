import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { CircleAccessService } from '../circles/circle-access.service';
import { notFound } from '../common/errors';
import { endOfDay, weekKey } from '../common/periods';
import { PrismaService, Tx } from '../prisma/prisma.service';
import { RealtimeBus } from '../realtime/realtime-bus';
import { GameEffects } from './effects';

/**
 * Friendly territory game around public landmarks. Points are per circle and per weekly event
 * period; the circle with the most points (at least CLAIM_MIN) owns the territory until its
 * hold decays. Only aggregated circle scores are exposed: never who visited, never when
 * (beyond the day of a claim).
 */
export const TERRITORY_RULES = { claimMinScore: 3, holdDays: 7 };

@Injectable()
export class TerritoryService {
  private readonly log = new Logger('Territory');

  constructor(private readonly prisma: PrismaService, private readonly access: CircleAccessService, private readonly bus: RealtimeBus) {}

  /** One point per player, per territory, per day, for their chosen team circle. */
  async recordVisit(tx: Tx, userId: string, teamCircleId: string, landmarkId: string, fx: GameEffects, now = new Date()) {
    const territory = await tx.territory.findUnique({ where: { landmarkId } });
    if (!territory) return;
    // Row lock: concurrent visits to the same territory are serialized, so ownership changes are consistent.
    await tx.$queryRaw`SELECT "id" FROM "Territory" WHERE "id" = ${territory.id}::uuid FOR UPDATE`;
    const dayStart = new Date(endOfDay(now).getTime() - 86_400_000);
    const already = await tx.territoryEvent.findFirst({ where: { territoryId: territory.id, userId, type: 'VISIT', createdAt: { gte: dayStart } } });
    if (already) return;

    const period = weekKey(now);
    await tx.territoryEvent.create({ data: { territoryId: territory.id, userId, circleId: teamCircleId, type: 'VISIT', points: 1 } });
    await tx.territoryScore.upsert({
      where: { territoryId_circleId_periodKey: { territoryId: territory.id, circleId: teamCircleId, periodKey: period } },
      create: { territoryId: territory.id, circleId: teamCircleId, periodKey: period, score: 1 },
      update: { score: { increment: 1 } },
    });
    const fresh = await tx.territory.update({ where: { id: territory.id }, data: { totalVisits: { increment: 1 } } });
    const top = await tx.territoryScore.findFirst({ where: { territoryId: territory.id, periodKey: period }, orderBy: [{ score: 'desc' }, { circleId: 'asc' }] });
    if (!top || top.score < TERRITORY_RULES.claimMinScore) return;

    const holdUntil = new Date(now.getTime() + TERRITORY_RULES.holdDays * 86_400_000);
    if (fresh.ownerCircleId === top.circleId) {
      await tx.territory.update({ where: { id: territory.id }, data: { ownerScore: top.score, expiresAt: holdUntil, version: { increment: 1 } } });
      return;
    }
    const ownerScore = fresh.ownerCircleId
      ? ((await tx.territoryScore.findUnique({ where: { territoryId_circleId_periodKey: { territoryId: territory.id, circleId: fresh.ownerCircleId, periodKey: period } } }))?.score ?? 0)
      : 0;
    if (top.score <= ownerScore) return; // ties keep the current holder
    await tx.territory.update({
      where: { id: territory.id },
      data: { ownerCircleId: top.circleId, ownerScore: top.score, claimedAt: now, expiresAt: holdUntil, version: { increment: 1 } },
    });
    await tx.territoryEvent.create({ data: { territoryId: territory.id, circleId: top.circleId, userId: top.circleId === teamCircleId ? userId : null, type: 'CLAIM', points: top.score } });
    fx.territoryChanges.push({ territoryId: territory.id, name: territory.name, ownerCircleId: top.circleId, previousOwnerCircleId: fresh.ownerCircleId });
  }

  @Cron(CronExpression.EVERY_HOUR)
  async decay(now = new Date()) {
    const expired = await this.prisma.territory.findMany({ where: { ownerCircleId: { not: null }, expiresAt: { lte: now } } });
    for (const t of expired) {
      const r = await this.prisma.territory.updateMany({
        where: { id: t.id, version: t.version },
        data: { ownerCircleId: null, ownerScore: 0, claimedAt: null, expiresAt: null, version: { increment: 1 } },
      });
      if (r.count === 0) continue;
      await this.prisma.territoryEvent.create({ data: { territoryId: t.id, circleId: t.ownerCircleId, type: 'DECAY' } });
      const members = await this.prisma.circleMembership.findMany({ where: { circleId: t.ownerCircleId! }, select: { userId: true } });
      this.bus.publish({
        recipients: members.map((m) => m.userId),
        type: 'territory.update',
        data: { territoryId: t.id, name: t.name, ownerCircleId: null, previousOwnerCircleId: t.ownerCircleId },
      });
    }
    if (expired.length) this.log.log(`decayed ${expired.length} territory hold(s)`);
  }

  private async circleNames(ids: string[]) {
    const rows = await this.prisma.circle.findMany({ where: { id: { in: ids } }, select: { id: true, name: true } });
    return new Map(rows.map((r) => [r.id, r.name]));
  }

  /** Territories inside a viewport. Owner circle names are public game state; members are not. */
  async inBounds(userId: string, b: { north: number; south: number; east: number; west: number }) {
    const myCircles = new Set(await this.access.circleIdsOf(userId));
    const rows = await this.prisma.territory.findMany({
      where: { landmark: { active: true, latitude: { gte: b.south, lte: b.north }, longitude: { gte: b.west, lte: b.east } } },
      include: { landmark: true },
      take: 200,
    });
    const names = await this.circleNames(rows.map((r) => r.ownerCircleId).filter((x): x is string => !!x));
    return rows.map((t) => ({
      id: t.id,
      name: t.name,
      landmarkId: t.landmarkId,
      latitude: t.landmark.latitude,
      longitude: t.landmark.longitude,
      radiusM: t.landmark.radiusM,
      ownerCircleId: t.ownerCircleId,
      ownerCircleName: t.ownerCircleId ? names.get(t.ownerCircleId) : null,
      ownedByMyCircle: !!t.ownerCircleId && myCircles.has(t.ownerCircleId),
      expiresAt: t.expiresAt,
    }));
  }

  async detail(userId: string, territoryId: string) {
    const t = await this.prisma.territory.findUnique({ where: { id: territoryId }, include: { landmark: true } });
    if (!t) throw notFound('Territory');
    const period = weekKey();
    const [scores, history, profile] = await Promise.all([
      this.prisma.territoryScore.findMany({ where: { territoryId, periodKey: period }, orderBy: { score: 'desc' }, take: 10 }),
      this.prisma.territoryEvent.findMany({ where: { territoryId, type: { in: ['CLAIM', 'DECAY'] } }, orderBy: { createdAt: 'desc' }, take: 20 }),
      this.prisma.userProfile.findUnique({ where: { userId } }),
    ]);
    const names = await this.circleNames([...scores.map((s) => s.circleId), ...history.map((h) => h.circleId), t.ownerCircleId].filter((x): x is string => !!x));
    const myCircles = new Set(await this.access.circleIdsOf(userId));
    return {
      id: t.id,
      name: t.name,
      landmark: { id: t.landmark.id, name: t.landmark.name, category: t.landmark.category, latitude: t.landmark.latitude, longitude: t.landmark.longitude, radiusM: t.landmark.radiusM },
      ownerCircleId: t.ownerCircleId,
      ownerCircleName: t.ownerCircleId ? names.get(t.ownerCircleId) : null,
      ownerScore: t.ownerScore,
      claimedAt: t.claimedAt,
      expiresAt: t.expiresAt,
      periodKey: period,
      rules: TERRITORY_RULES,
      myTeamCircleId: profile?.teamCircleId ?? null,
      leaderboard: scores.map((s, i) => ({ rank: i + 1, circleId: s.circleId, circleName: names.get(s.circleId) ?? 'A circle', score: s.score, isMine: myCircles.has(s.circleId) })),
      // Day precision only: exact claim times could hint at when someone was there.
      history: history.map((h) => ({ type: h.type, circleName: h.circleId ? names.get(h.circleId) ?? 'A circle' : null, day: h.createdAt.toISOString().slice(0, 10) })),
    };
  }
}
