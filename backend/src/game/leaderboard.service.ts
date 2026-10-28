import { Injectable, Logger } from '@nestjs/common';
import { Cron } from '@nestjs/schedule';
import { Prisma } from '@prisma/client';
import { CircleAccessService } from '../circles/circle-access.service';
import { badRequest } from '../common/errors';
import { seasonKey, startOfWeek, weekKey } from '../common/periods';
import { PrismaService } from '../prisma/prisma.service';

export type BoardScope = 'friends' | 'circle' | 'global';
export type BoardMetric = 'xp' | 'tiles' | 'quests';
export type BoardPeriod = 'week' | 'season';

interface Query {
  scope: BoardScope;
  metric: BoardMetric;
  period: BoardPeriod;
  circleId?: string;
  limit: number;
  offset: number;
}

function seasonStart(d = new Date()): Date {
  const [y, q] = seasonKey(d).split('-Q').map(Number);
  // Season boundaries follow the game timezone like every other period.
  const offsetMin = Number(process.env.GAME_TZ_OFFSET_MINUTES ?? 420);
  return new Date(Date.UTC(y, (q - 1) * 3, 1) - offsetMin * 60_000);
}

/**
 * Rankings never contain coordinates, places or timestamps of activity, only aggregate scores.
 * Players who opted out of rankings are left out of every board except their own row, which
 * only they can see.
 */
@Injectable()
export class LeaderboardService {
  private readonly log = new Logger('Leaderboards');

  constructor(private readonly prisma: PrismaService, private readonly access: CircleAccessService) {}

  async board(viewerId: string, q: Query) {
    const since = q.period === 'week' ? startOfWeek() : seasonStart();
    let pool: string[] | null;
    if (q.scope === 'circle') {
      if (!q.circleId) throw badRequest('CIRCLE_REQUIRED', 'Pick a circle');
      await this.access.requireMember(q.circleId, viewerId);
      pool = (await this.prisma.circleMembership.findMany({ where: { circleId: q.circleId }, select: { userId: true } })).map((m) => m.userId);
    } else if (q.scope === 'friends') {
      pool = [viewerId, ...(await this.access.circleMates(viewerId))];
    } else {
      pool = null;
    }

    const optedOut = new Set(
      (await this.prisma.userPrivacySettings.findMany({ where: { showInLeaderboards: false, ...(pool ? { userId: { in: pool } } : {}) }, select: { userId: true } })).map(
        (p) => p.userId,
      ),
    );
    const scores = await this.scores(q.metric, since, pool);
    const ranked = scores.filter((s) => !optedOut.has(s.userId) || s.userId === viewerId);
    if (pool) for (const id of pool) if (!ranked.some((r) => r.userId === id) && (!optedOut.has(id) || id === viewerId)) ranked.push({ userId: id, score: 0 });
    ranked.sort((a, b) => b.score - a.score || a.userId.localeCompare(b.userId));

    const page = ranked.slice(q.offset, q.offset + q.limit);
    const profiles = await this.prisma.userProfile.findMany({
      where: { userId: { in: [...page.map((p) => p.userId), viewerId] } },
      include: { user: { select: { privacy: { select: { leaderboardAlias: true } } } } },
    });
    const byId = new Map(profiles.map((p) => [p.userId, p]));
    const name = (id: string) => {
      const p = byId.get(id);
      return p?.user.privacy?.leaderboardAlias || p?.displayName || 'Player';
    };
    const rankOf = (id: string) => {
      const i = ranked.findIndex((r) => r.userId === id);
      return i < 0 ? null : i + 1;
    };
    const showAvatar = q.scope !== 'global';
    return {
      scope: q.scope,
      metric: q.metric,
      period: q.period,
      periodKey: q.period === 'week' ? weekKey() : seasonKey(),
      total: ranked.length,
      entries: page.map((r, i) => ({
        rank: q.offset + i + 1,
        userId: r.userId,
        name: name(r.userId),
        avatarKey: showAvatar ? byId.get(r.userId)?.avatarKey : null,
        level: byId.get(r.userId)?.level ?? 1,
        score: r.score,
        isMe: r.userId === viewerId,
        hiddenFromOthers: optedOut.has(r.userId),
      })),
      me: { rank: rankOf(viewerId), score: ranked.find((r) => r.userId === viewerId)?.score ?? 0, optedOut: optedOut.has(viewerId) },
    };
  }

  private async scores(metric: BoardMetric, since: Date, pool: string[] | null, until = new Date('9999-01-01')): Promise<{ userId: string; score: number }[]> {
    const poolFilter = pool ? Prisma.sql`AND "userId" = ANY(${pool}::uuid[])` : Prisma.empty;
    let rows: { userId: string; score: bigint | number }[];
    if (metric === 'xp') {
      rows = await this.prisma.$queryRaw`SELECT "userId", sum("amount")::bigint AS score FROM "XPTransaction" WHERE "createdAt" >= ${since} AND "createdAt" < ${until} ${poolFilter} GROUP BY "userId" ORDER BY score DESC LIMIT 1000`;
    } else if (metric === 'tiles') {
      rows = await this.prisma.$queryRaw`SELECT "userId", count(*)::bigint AS score FROM "TileDiscovery" WHERE "discoveredAt" >= ${since} AND "discoveredAt" < ${until} ${poolFilter} GROUP BY "userId" ORDER BY score DESC LIMIT 1000`;
    } else {
      rows = await this.prisma.$queryRaw`SELECT "userId", count(*)::bigint AS score FROM "QuestCompletion" WHERE "claimedAt" >= ${since} AND "claimedAt" < ${until} ${poolFilter} GROUP BY "userId" ORDER BY score DESC LIMIT 1000`;
    }
    return rows.map((r) => ({ userId: r.userId, score: Number(r.score) }));
  }

  /** Every Monday 00:10 WIB, freeze last week's global boards so results stay viewable. */
  @Cron('10 17 * * 0')
  async snapshotLastWeek() {
    const lastWeekDate = new Date(Date.now() - 2 * 86_400_000);
    const periodKey = weekKey(lastWeekDate);
    const since = startOfWeek(lastWeekDate);
    const until = new Date(since.getTime() + 7 * 86_400_000);
    for (const [board, metric] of [['WEEKLY_XP', 'xp'], ['EXPLORATION', 'tiles'], ['QUESTS', 'quests']] as const) {
      const scores = (await this.scores(metric, since, null, until)).slice(0, 100);
      const optedOut = new Set((await this.prisma.userPrivacySettings.findMany({ where: { showInLeaderboards: false }, select: { userId: true } })).map((p) => p.userId));
      const eligible = scores.filter((s) => !optedOut.has(s.userId));
      const profiles = await this.prisma.userProfile.findMany({ where: { userId: { in: eligible.map((e) => e.userId) } }, include: { user: { select: { privacy: true } } } });
      const byId = new Map(profiles.map((p) => [p.userId, p]));
      await this.prisma.leaderboardSnapshot.createMany({
        data: eligible.map((e, i) => ({
          board,
          periodKey,
          userId: e.userId,
          alias: byId.get(e.userId)?.user.privacy?.leaderboardAlias || byId.get(e.userId)?.displayName || 'Player',
          rank: i + 1,
          score: e.score,
        })),
        skipDuplicates: true,
      });
    }
    this.log.log(`snapshotted leaderboards for ${periodKey}`);
  }

  async history(board: string, periodKey: string) {
    const rows = await this.prisma.leaderboardSnapshot.findMany({ where: { board, periodKey, scopeId: null }, orderBy: { rank: 'asc' }, take: 100 });
    return rows.map((r) => ({ rank: r.rank, name: r.alias, score: r.score }));
  }
}
