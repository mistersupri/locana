import { Injectable } from '@nestjs/common';
import { boundingBox, haversineM, parseTileKey, tileBounds, tileFor, tileKey } from '../common/geo';
import { dayKey } from '../common/periods';
import { isOnFootSpeed } from '../location/location-validation';
import { PrismaService, Tx } from '../prisma/prisma.service';
import { AchievementsService } from './achievements.service';
import { ChallengesService } from './challenges.service';
import { emptyEffects, GameEffects } from './effects';
import { EffectsPublisher } from './effects-publisher.service';
import { GameEvent } from './quest-engine';
import { QuestsService } from './quests.service';
import { TerritoryService } from './territory.service';
import { XpService } from './xp.service';

export interface TrustedSample {
  latitude: number;
  longitude: number;
  accuracyM: number;
  recordedAt: Date;
  speedMps: number | null;
  /** distance from the previous accepted sample after jitter filtering */
  distanceFromPreviousM: number;
  impliedSpeedMps: number | null;
  ref: string;
}

/**
 * Tunables for exploration rewards. Kept here (not scattered) so balancing is one edit.
 * A day's tile XP is capped so a long drive cannot farm unlimited rewards.
 */
export const EXPLORATION_RULES = {
  tileXp: 10,
  maxRewardedTilesPerDay: 40,
  maxDistancePerSampleM: 500,
  activeDayDistanceM: 300,
  landmarkMaxAccuracyM: 40,
};

@Injectable()
export class ExplorationService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly quests: QuestsService,
    private readonly achievements: AchievementsService,
    private readonly territories: TerritoryService,
    private readonly challenges: ChallengesService,
    private readonly publisher: EffectsPublisher,
    private readonly xp: XpService,
  ) {}

  /** Processes trusted samples for a player who enabled exploration. Server decides every reward. */
  async process(userId: string, samples: TrustedSample[]): Promise<GameEffects> {
    const fx = emptyEffects();
    if (samples.length === 0) return fx;
    await this.quests.ensureQuests(userId);
    for (const s of samples) {
      await this.prisma.$transaction(async (tx) => {
        await this.processOne(tx, userId, s, fx);
      });
    }
    await this.prisma.$transaction(async (tx) => this.achievements.evaluate(tx, userId, fx));
    await this.publisher.publish(userId, fx);
    return fx;
  }

  private async processOne(tx: Tx, userId: string, s: TrustedSample, fx: GameEffects) {
    const profile = await tx.userProfile.findUniqueOrThrow({ where: { userId } });
    const day = dayKey(s.recordedAt);
    const dayDate = new Date(`${day}T00:00:00Z`);
    const events: GameEvent[] = [];
    const stat = await tx.dailyActivityStat.upsert({ where: { userId_day: { userId, day: dayDate } }, create: { userId, day: dayDate }, update: {} });

    // ── tiles ──
    const tile = tileFor(s.latitude, s.longitude);
    const tid = tileKey(tile);
    await tx.$executeRaw`INSERT INTO "MapTile" ("id", "zoom", "x", "y") VALUES (${tid}, ${tile.z}, ${tile.x}, ${tile.y}) ON CONFLICT DO NOTHING`;
    const disc = await tx.$queryRaw<{ inserted: boolean }[]>`
      INSERT INTO "TileDiscovery" ("userId", "tileId") VALUES (${userId}::uuid, ${tid})
      ON CONFLICT ("userId", "tileId") DO UPDATE SET "visits" = "TileDiscovery"."visits" + 1, "lastVisitAt" = now()
      RETURNING (xmax = 0) AS inserted`;
    const newTile = disc[0]?.inserted === true;
    if (newTile) {
      fx.newTiles.push(tid);
      await tx.userProfile.update({ where: { userId }, data: { tilesDiscovered: { increment: 1 } } });
      await tx.dailyActivityStat.update({ where: { userId_day: { userId, day: dayDate } }, data: { tiles: { increment: 1 } } });
      if (stat.tiles < EXPLORATION_RULES.maxRewardedTilesPerDay) {
        await this.xp.grant(tx, userId, EXPLORATION_RULES.tileXp, 'TILE_DISCOVERY', tid, fx);
      }
      events.push({ kind: 'TILE', tileId: tid });
      const circles = await tx.circleMembership.findMany({ where: { userId }, select: { circleId: true } });
      for (const c of circles) events.push({ kind: 'CIRCLE_TILE', circleId: c.circleId, tileId: tid });
      await this.challenges.bump(tx, userId, 'TILES', 1, fx);
    }

    // ── landmarks ──
    if (s.accuracyM <= EXPLORATION_RULES.landmarkMaxAccuracyM) {
      const bb = boundingBox(s.latitude, s.longitude, 2_000);
      const nearby = await tx.landmark.findMany({
        where: { active: true, latitude: { gte: bb.minLat, lte: bb.maxLat }, longitude: { gte: bb.minLon, lte: bb.maxLon } },
      });
      for (const lm of nearby) {
        if (haversineM(s.latitude, s.longitude, lm.latitude, lm.longitude) > lm.radiusM) continue;
        const v = await tx.$queryRaw<{ inserted: boolean }[]>`
          INSERT INTO "LandmarkVisit" ("userId", "landmarkId") VALUES (${userId}::uuid, ${lm.id}::uuid)
          ON CONFLICT ("userId", "landmarkId") DO UPDATE SET "count" = "LandmarkVisit"."count" + 1, "lastAt" = now()
          RETURNING (xmax = 0) AS inserted`;
        const first = v[0]?.inserted === true;
        fx.landmarks.push({ id: lm.id, name: lm.name, firstVisit: first });
        if (first) {
          await this.xp.grant(tx, userId, lm.rewardXp, 'LANDMARK', lm.id, fx);
          if (lm.collectibleCode) await this.xp.grantCollectible(tx, userId, lm.collectibleCode, `landmark:${lm.code}`, fx);
          await tx.dailyActivityStat.update({ where: { userId_day: { userId, day: dayDate } }, data: { landmarks: { increment: 1 } } });
          await this.challenges.bump(tx, userId, 'LANDMARKS', 1, fx);
        }
        events.push({ kind: 'LANDMARK', landmarkId: lm.id, setCode: lm.setCode });
        if (profile.teamCircleId) await this.territories.recordVisit(tx, userId, profile.teamCircleId, lm.id, fx);
      }
    }

    // ── walking distance ──
    const speed = s.speedMps ?? s.impliedSpeedMps;
    let walked = 0;
    if (s.distanceFromPreviousM > 0 && isOnFootSpeed(speed)) {
      walked = Math.min(s.distanceFromPreviousM, EXPLORATION_RULES.maxDistancePerSampleM);
      await tx.dailyActivityStat.update({ where: { userId_day: { userId, day: dayDate } }, data: { distanceM: { increment: walked } } });
      await tx.userProfile.update({ where: { userId }, data: { totalDistanceM: { increment: walked } } });
      events.push({ kind: 'DISTANCE', meters: walked, sampleRef: s.ref });
    }

    // ── active day and streak ──
    const becameActive =
      (newTile && stat.tiles === 0) ||
      (stat.distanceM < EXPLORATION_RULES.activeDayDistanceM && stat.distanceM + walked >= EXPLORATION_RULES.activeDayDistanceM);
    if (becameActive) {
      events.push({ kind: 'ACTIVE_DAY', dayKey: day });
      await this.updateStreak(tx, userId, dayDate);
    }

    await this.quests.applyEvents(tx, userId, events, fx);
  }

  private async updateStreak(tx: Tx, userId: string, today: Date) {
    const p = await tx.userProfile.findUniqueOrThrow({ where: { userId } });
    const last = p.lastActiveDay;
    if (last && last.getTime() === today.getTime()) return;
    const yesterday = new Date(today.getTime() - 86_400_000);
    const streak = last && last.getTime() === yesterday.getTime() ? p.currentStreak + 1 : 1;
    await tx.userProfile.update({
      where: { userId },
      data: { currentStreak: streak, longestStreak: Math.max(p.longestStreak, streak), lastActiveDay: today },
    });
  }

  /** The player's own discovered tiles inside a viewport (never anyone else's). */
  async myTiles(userId: string, b: { north: number; south: number; east: number; west: number }) {
    const nw = tileFor(b.north, b.west);
    const se = tileFor(b.south, b.east);
    const spanX = se.x - nw.x + 1;
    const spanY = se.y - nw.y + 1;
    if (spanX * spanY > 40_000) return { tiles: [], tooLarge: true };
    const rows = await this.prisma.tileDiscovery.findMany({
      where: { userId, tile: { zoom: nw.z, x: { gte: nw.x, lte: se.x }, y: { gte: nw.y, lte: se.y } } },
      select: { tileId: true, discoveredAt: true, visits: true },
      take: 5_000,
    });
    return {
      tooLarge: false,
      tiles: rows.map((r) => {
        const t = parseTileKey(r.tileId)!;
        return { id: r.tileId, ...tileBounds(t), discoveredAt: r.discoveredAt, visits: r.visits };
      }),
    };
  }

  async landmarksNear(userId: string, lat: number, lon: number, radiusM = 5_000, query?: string) {
    const bb = boundingBox(lat, lon, radiusM);
    const rows = await this.prisma.landmark.findMany({
      where: {
        active: true,
        ...(query ? { name: { contains: query, mode: 'insensitive' } } : { latitude: { gte: bb.minLat, lte: bb.maxLat }, longitude: { gte: bb.minLon, lte: bb.maxLon } }),
      },
      include: { visits: { where: { userId } }, territory: { select: { id: true, ownerCircleId: true } } },
      take: 100,
    });
    return rows
      .map((l) => ({
        id: l.id,
        code: l.code,
        name: l.name,
        description: l.description,
        category: l.category,
        latitude: l.latitude,
        longitude: l.longitude,
        radiusM: l.radiusM,
        rewardXp: l.rewardXp,
        setCode: l.setCode,
        visited: l.visits.length > 0,
        territoryId: l.territory?.id ?? null,
        distanceM: Math.round(haversineM(lat, lon, l.latitude, l.longitude)),
      }))
      .sort((a, b) => a.distanceM - b.distanceM);
  }

  async stats(userId: string) {
    const p = await this.prisma.userProfile.findUniqueOrThrow({ where: { userId } });
    const since = new Date(`${dayKey(new Date(Date.now() - 6 * 86_400_000))}T00:00:00Z`);
    const [landmarks, days] = await Promise.all([
      this.prisma.landmarkVisit.count({ where: { userId } }),
      this.prisma.dailyActivityStat.findMany({ where: { userId, day: { gte: since } }, orderBy: { day: 'asc' } }),
    ]);
    return {
      tilesDiscovered: p.tilesDiscovered,
      landmarksVisited: landmarks,
      totalDistanceM: Math.round(p.totalDistanceM),
      currentStreak: p.currentStreak,
      longestStreak: p.longestStreak,
      questsCompleted: p.questsCompleted,
      last7Days: days.map((d) => ({ day: d.day.toISOString().slice(0, 10), distanceM: Math.round(d.distanceM), tiles: d.tiles, landmarks: d.landmarks, xp: d.xp })),
    };
  }

  /** Circle exploration progress: aggregated counts only, opt-in per member. */
  async circleStats(circleId: string) {
    const members = await this.prisma.circleMembership.findMany({
      where: { circleId },
      include: { user: { include: { profile: true, privacy: true } } },
    });
    const sharing = members.filter((m) => m.user.privacy?.shareExplorationWithCircles !== false);
    return {
      totalTiles: sharing.reduce((n, m) => n + (m.user.profile?.tilesDiscovered ?? 0), 0),
      members: sharing.map((m) => ({
        userId: m.userId,
        displayName: m.user.profile?.displayName ?? m.user.username,
        avatarKey: m.user.profile?.avatarKey,
        tilesDiscovered: m.user.profile?.tilesDiscovered ?? 0,
        level: m.user.profile?.level ?? 1,
      })),
      hiddenMembers: members.length - sharing.length,
    };
  }
}
