import { Injectable } from '@nestjs/common';
import { GAME_TZ_OFFSET_MIN } from '../common/periods';
import { PrismaService, Tx } from '../prisma/prisma.service';
import { GameEffects } from './effects';
import { XpService } from './xp.service';

export type AchievementMetric =
  | 'tilesDiscovered'
  | 'landmarksVisited'
  | 'questsCompleted'
  | 'level'
  | 'streak'
  | 'weekendTiles'
  | 'circleQuests'
  | 'territoriesClaimed';

interface Criteria {
  metric: AchievementMetric;
  gte: number;
}

@Injectable()
export class AchievementsService {
  constructor(private readonly prisma: PrismaService, private readonly xp: XpService) {}

  private async metric(tx: Tx, userId: string, m: AchievementMetric): Promise<number> {
    switch (m) {
      case 'tilesDiscovered':
      case 'questsCompleted':
      case 'level': {
        const p = await tx.userProfile.findUniqueOrThrow({ where: { userId } });
        return p[m];
      }
      case 'streak':
        return (await tx.userProfile.findUniqueOrThrow({ where: { userId } })).longestStreak;
      case 'landmarksVisited':
        return tx.landmarkVisit.count({ where: { userId } });
      case 'weekendTiles': {
        const r = await tx.$queryRaw<{ n: bigint }[]>`
          SELECT count(*)::bigint AS n FROM "TileDiscovery"
          WHERE "userId" = ${userId}::uuid
            AND extract(dow FROM "discoveredAt" + make_interval(mins => ${GAME_TZ_OFFSET_MIN}::int)) IN (0, 6)`;
        return Number(r[0]?.n ?? 0);
      }
      case 'circleQuests':
        return tx.questCompletion.count({ where: { userId, quest: { condition: 'CIRCLE_TILES' } } });
      case 'territoriesClaimed':
        return tx.territoryEvent.count({ where: { userId, type: 'CLAIM' } });
    }
  }

  /** Unlocks every achievement whose criteria the player now meets. Safe to call repeatedly. */
  async evaluate(tx: Tx, userId: string, fx: GameEffects, depth = 0) {
    const locked = await tx.achievementDefinition.findMany({
      where: { users: { none: { userId } } },
      orderBy: { sortOrder: 'asc' },
    });
    const cache = new Map<AchievementMetric, number>();
    let unlockedAny = false;
    for (const def of locked) {
      const c = def.criteria as unknown as Criteria;
      if (!c?.metric) continue;
      if (!cache.has(c.metric)) cache.set(c.metric, await this.metric(tx, userId, c.metric));
      if ((cache.get(c.metric) ?? 0) < c.gte) continue;
      const rows = await tx.$queryRaw<{ userId: string }[]>`
        INSERT INTO "UserAchievement" ("userId", "achievementId") VALUES (${userId}::uuid, ${def.id}::uuid)
        ON CONFLICT DO NOTHING RETURNING "userId"`;
      if (rows.length === 0) continue;
      unlockedAny = true;
      fx.achievements.push({ code: def.code, name: def.name, rarity: def.rarity, rewardXp: def.rewardXp });
      await this.xp.grant(tx, userId, def.rewardXp, 'ACHIEVEMENT', def.code, fx);
      if (def.rewardCollectibleCode) await this.xp.grantCollectible(tx, userId, def.rewardCollectibleCode, `achievement:${def.code}`, fx);
    }
    // XP from an achievement can level the player up, which can unlock a level achievement.
    if (unlockedAny && depth < 3) await this.evaluate(tx, userId, fx, depth + 1);
  }

  async list(userId: string) {
    const [defs, mine] = await Promise.all([
      this.prisma.achievementDefinition.findMany({ orderBy: { sortOrder: 'asc' } }),
      this.prisma.userAchievement.findMany({ where: { userId } }),
    ]);
    const unlocked = new Map(mine.map((m) => [m.achievementId, m.unlockedAt]));
    const progress = new Map<AchievementMetric, number>();
    for (const d of defs) {
      const c = d.criteria as unknown as Criteria;
      if (c?.metric && !progress.has(c.metric)) progress.set(c.metric, await this.metric(this.prisma, userId, c.metric));
    }
    return defs.map((d) => {
      const c = d.criteria as unknown as Criteria;
      return {
        code: d.code,
        name: d.name,
        description: d.description,
        rarity: d.rarity,
        iconKey: d.iconKey,
        rewardXp: d.rewardXp,
        unlockedAt: unlocked.get(d.id) ?? null,
        progress: Math.min(progress.get(c.metric) ?? 0, c.gte),
        target: c.gte,
      };
    });
  }
}
