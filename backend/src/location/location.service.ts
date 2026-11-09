import { HttpStatus, Inject, Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { CircleAccessService } from '../circles/circle-access.service';
import { STALE_AFTER_MS } from '../circles/circles.service';
import { ApiError } from '../common/errors';
import { haversineM } from '../common/geo';
import { APP_ENV, AppEnv } from '../config/env';
import { ExplorationService, TrustedSample } from '../game/exploration.service';
import { PlacesService } from '../places/places.service';
import { PrismaService } from '../prisma/prisma.service';
import { RealtimeBus } from '../realtime/realtime-bus';
import { LocationSampleDto } from './location.dto';
import { PreviousSample, validateSample } from './location-validation';
import { buildTimeline, CurrentMovement, currentMovement, TimelinePoint } from './timeline';

export type MemberLocationStatus = 'LIVE' | 'STALE' | 'UNAVAILABLE' | 'HIDDEN';

/** History rows are thinned: at most one per minute unless the person moved this far. */
const HISTORY_MIN_GAP_MS = 30_000;

@Injectable()
export class LocationService {
  private readonly log = new Logger('Location');

  constructor(
    private readonly prisma: PrismaService,
    private readonly access: CircleAccessService,
    private readonly bus: RealtimeBus,
    private readonly places: PlacesService,
    private readonly exploration: ExplorationService,
    @Inject(APP_ENV) private readonly env: AppEnv,
  ) {}

  /**
   * Accepts a batch of samples (live or queued while offline). The server keeps a location
   * only for features the user turned on: live sharing and/or exploration. With both off the
   * upload is refused, which also tells a misbehaving client to stop collecting.
   */
  async ingest(userId: string, input: LocationSampleDto[]) {
    const privacy = await this.prisma.userPrivacySettings.findUniqueOrThrow({ where: { userId } });
    const sharingCircles = await this.access.sharingCircleIds(userId);
    const sharing = sharingCircles.length > 0;
    if (!sharing && !privacy.explorationEnabled) {
      throw new ApiError(HttpStatus.CONFLICT, 'LOCATION_NOT_NEEDED', 'Location sharing and exploration are both off, so no location is accepted');
    }

    const samples = input
      .map((s, index) => ({ ...s, index, at: new Date(s.recordedAt) }))
      .sort((a, b) => a.at.getTime() - b.at.getTime() || a.sequence - b.sequence);

    const latest = await this.prisma.latestLocation.findUnique({ where: { userId } });
    let prev: PreviousSample | null = latest
      ? { latitude: latest.latitude, longitude: latest.longitude, accuracyM: latest.accuracyM, recordedAt: latest.recordedAt, sequence: Number(latest.sequence) }
      : null;

    const rejected: { index: number; reason: string }[] = [];
    const accepted: (typeof samples)[number][] = [];
    const trusted: TrustedSample[] = [];
    const risks: { userId: string; kind: string; weight: number }[] = [];
    const now = new Date();

    for (const s of samples) {
      const v = validateSample(
        { latitude: s.latitude, longitude: s.longitude, accuracyM: s.accuracyM, recordedAt: s.at, sequence: s.sequence, speedMps: s.speedMps, source: s.source, isMock: s.isMock },
        prev,
        now,
      );
      for (const r of v.risks) risks.push({ userId, kind: r.kind, weight: r.weight });
      if (!v.accepted) {
        rejected.push({ index: s.index, reason: v.rejection! });
        continue;
      }
      accepted.push(s);
      if (v.trusted && privacy.explorationEnabled) {
        trusted.push({
          latitude: s.latitude,
          longitude: s.longitude,
          accuracyM: s.accuracyM,
          recordedAt: s.at,
          speedMps: s.speedMps ?? null,
          distanceFromPreviousM: v.distanceFromPreviousM,
          impliedSpeedMps: v.impliedSpeedMps,
          ref: `${s.at.getTime()}:${s.sequence}`,
        });
      }
      prev = { latitude: s.latitude, longitude: s.longitude, accuracyM: s.accuracyM, recordedAt: s.at, sequence: s.sequence };
    }

    if (risks.length) await this.prisma.riskSignal.createMany({ data: risks });
    const newest = accepted[accepted.length - 1];
    if (newest) {
      const data = {
        latitude: newest.latitude,
        longitude: newest.longitude,
        accuracyM: newest.accuracyM,
        speedMps: newest.speedMps ?? null,
        bearingDeg: newest.bearingDeg ?? null,
        altitudeM: newest.altitudeM ?? null,
        source: newest.source,
        activity: newest.activity ?? null,
        sequence: BigInt(newest.sequence),
        recordedAt: newest.at,
        receivedAt: now,
        batteryPct: newest.batteryPct ?? null,
      };
      // Conditional on recordedAt so a slow request can never overwrite a newer position.
      await this.prisma.$executeRaw`
        INSERT INTO "LatestLocation" ("userId","latitude","longitude","accuracyM","speedMps","bearingDeg","altitudeM","source","activity","sequence","recordedAt","receivedAt","batteryPct")
        VALUES (${userId}::uuid, ${data.latitude}, ${data.longitude}, ${data.accuracyM}, ${data.speedMps}, ${data.bearingDeg}, ${data.altitudeM}, ${data.source}, ${data.activity}, ${data.sequence}, ${data.recordedAt}, ${data.receivedAt}, ${data.batteryPct})
        ON CONFLICT ("userId") DO UPDATE SET
          "latitude" = EXCLUDED."latitude", "longitude" = EXCLUDED."longitude", "accuracyM" = EXCLUDED."accuracyM",
          "speedMps" = EXCLUDED."speedMps", "bearingDeg" = EXCLUDED."bearingDeg", "altitudeM" = EXCLUDED."altitudeM",
          "source" = EXCLUDED."source", "activity" = EXCLUDED."activity", "sequence" = EXCLUDED."sequence",
          "recordedAt" = EXCLUDED."recordedAt", "receivedAt" = EXCLUDED."receivedAt", "batteryPct" = EXCLUDED."batteryPct"
        WHERE "LatestLocation"."recordedAt" < EXCLUDED."recordedAt"`;

      if (sharing && privacy.historyEnabled) await this.appendHistory(userId, accepted);
      if (sharing) {
        await this.places.processSamples(
          userId,
          sharingCircles,
          accepted.map((s) => ({ lat: s.latitude, lon: s.longitude, accuracyM: s.accuracyM, at: s.at })),
        );
        await this.broadcast(userId, newest, await this.movementNow(userId, newest));
      }
    }

    const fx = trusted.length ? await this.exploration.process(userId, trusted) : null;
    return {
      accepted: accepted.length,
      rejected,
      latestRecordedAt: newest?.at ?? latest?.recordedAt ?? null,
      sharingActive: sharing,
      game: fx && {
        xpGained: fx.xpGained,
        newTiles: fx.newTiles,
        landmarks: fx.landmarks,
        levelUps: fx.levelUps,
        achievements: fx.achievements,
        questUpdates: [...fx.questProgress.values()].filter((q) => q.userId === userId),
      },
    };
  }

  private async appendHistory(userId: string, accepted: (LocationSampleDto & { at: Date })[]) {
    const last = await this.prisma.locationHistory.findFirst({ where: { userId }, orderBy: { recordedAt: 'desc' } });
    let lastAt = last?.recordedAt.getTime() ?? 0;
    const rows = [];
    for (const s of accepted) {
      if (s.at.getTime() - lastAt < HISTORY_MIN_GAP_MS) continue;
      rows.push({ userId, latitude: s.latitude, longitude: s.longitude, accuracyM: s.accuracyM, speedMps: s.speedMps ?? null, activity: s.activity ?? null, recordedAt: s.at });
      lastAt = s.at.getTime();
    }
    if (rows.length) await this.prisma.locationHistory.createMany({ data: rows });
  }

  /** Sends the newest position to each authorized viewer, per circle. Nobody else receives it. */
  /** The same staying/moving verdict the locations endpoint gives, computed once per upload for the live event. */
  private async movementNow(userId: string, s: LocationSampleDto & { at: Date }) {
    const rows = await this.prisma.locationHistory.findMany({
      where: { userId, recordedAt: { gte: new Date(s.at.getTime() - 15 * 60_000), lt: s.at } },
      orderBy: { recordedAt: 'asc' },
      select: { latitude: true, longitude: true, accuracyM: true, speedMps: true, activity: true, recordedAt: true },
    });
    return currentMovement({ latitude: s.latitude, longitude: s.longitude, accuracyM: s.accuracyM, speedMps: s.speedMps ?? null, activity: s.activity ?? null, recordedAt: s.at }, rows);
  }

  private async broadcast(userId: string, s: LocationSampleDto & { at: Date }, movement: CurrentMovement) {
    const viewers = await this.access.locationViewers(userId);
    for (const [circleId, recipients] of viewers) {
      this.bus.publish({
        recipients,
        circleId,
        type: 'location.update',
        data: {
          circleId,
          userId,
          latitude: s.latitude,
          longitude: s.longitude,
          accuracyM: s.accuracyM,
          speedMps: s.speedMps ?? null,
          bearingDeg: s.bearingDeg ?? null,
          recordedAt: s.at,
          batteryPct: s.batteryPct ?? null,
          movement: movement.state,
          mode: movement.mode,
          stationarySince: movement.since,
        },
      });
    }
  }

  /** Member locations as the viewer is allowed to see them. Hidden members carry no coordinates or times. */
  async circleLocations(circleId: string, viewerId: string, now = new Date()) {
    await this.access.requireMember(circleId, viewerId);
    const members = await this.prisma.circleMembership.findMany({ where: { circleId }, select: { userId: true } });
    const visible = await this.access.visibleOwnersInCircle(circleId, viewerId, now);
    const ids = [...visible, viewerId];
    const locations = await this.prisma.latestLocation.findMany({ where: { userId: { in: ids } } });
    const byId = new Map(locations.map((l) => [l.userId, l]));
    // Recent history, only for people the viewer may see, to tell staying from moving.
    const recent = await this.prisma.locationHistory.findMany({
      where: { userId: { in: locations.map((l) => l.userId) }, recordedAt: { gte: new Date(now.getTime() - 12 * 3_600_000) } },
      orderBy: { recordedAt: 'asc' },
      select: { userId: true, latitude: true, longitude: true, accuracyM: true, speedMps: true, activity: true, recordedAt: true },
    });
    const historyOf = new Map<string, TimelinePoint[]>();
    for (const h of recent) (historyOf.get(h.userId) ?? historyOf.set(h.userId, []).get(h.userId)!).push(h);
    return members.map((m) => {
      const self = m.userId === viewerId;
      if (!self && !visible.has(m.userId)) return { userId: m.userId, status: 'HIDDEN' as MemberLocationStatus, location: null };
      const l = byId.get(m.userId);
      if (!l) return { userId: m.userId, status: 'UNAVAILABLE' as MemberLocationStatus, location: null };
      const stale = now.getTime() - l.recordedAt.getTime() > STALE_AFTER_MS;
      const movement = currentMovement(l, historyOf.get(m.userId) ?? []);
      return {
        userId: m.userId,
        status: (stale ? 'STALE' : 'LIVE') as MemberLocationStatus,
        location: {
          latitude: l.latitude,
          longitude: l.longitude,
          accuracyM: l.accuracyM,
          speedMps: l.speedMps,
          bearingDeg: l.bearingDeg,
          recordedAt: l.recordedAt,
          batteryPct: l.batteryPct,
          activity: l.activity,
          movement: movement.state,
          mode: movement.mode,
          stationarySince: movement.since,
        },
      };
    });
  }

  /**
   * Stays and trips for one member on one local day. The viewer needs the same permission as for
   * the live position (member, not hidden, not blocked), and only the history the member chose to keep.
   */
  async timeline(circleId: string, viewerId: string, ownerId: string, day: string, tzOffsetMinutes: number, now = new Date()) {
    await this.access.requireMember(circleId, viewerId);
    if (ownerId !== viewerId) {
      const visible = await this.access.visibleOwnersInCircle(circleId, viewerId, now);
      if (!visible.has(ownerId)) throw new ApiError(HttpStatus.FORBIDDEN, 'NOT_VISIBLE', 'This person is not sharing their location with you');
    }
    const start = new Date(Date.parse(`${day}T00:00:00Z`) - tzOffsetMinutes * 60_000);
    const end = new Date(start.getTime() + 86_400_000);
    const rows = await this.prisma.locationHistory.findMany({
      where: { userId: ownerId, recordedAt: { gte: start, lt: end } },
      orderBy: { recordedAt: 'asc' },
      take: 3_000,
    });
    // The newest position is not always in the thinned history yet; add it so the timeline is current.
    const latest = await this.prisma.latestLocation.findUnique({ where: { userId: ownerId } });
    const points: TimelinePoint[] = [...rows];
    if (latest && latest.recordedAt >= start && latest.recordedAt < end && (rows.length === 0 || latest.recordedAt > rows[rows.length - 1].recordedAt)) points.push(latest);
    const places = await this.prisma.savedPlace.findMany({ where: { circleId } });
    const segments = buildTimeline(points).map((seg) => {
      if (seg.type !== 'STAY') return seg;
      const hit = places.find((p) => haversineM(p.latitude, p.longitude, seg.latitude, seg.longitude) <= Math.max(p.radiusM, 100));
      return { ...seg, place: hit ? { id: hit.id, name: hit.name, type: hit.type } : null };
    });
    return { day, from: start, to: end, points: points.length, segments };
  }

  async history(userId: string, from?: Date, to?: Date) {
    const rows = await this.prisma.locationHistory.findMany({
      where: { userId, recordedAt: { gte: from ?? new Date(Date.now() - 86_400_000), lte: to ?? new Date() } },
      orderBy: { recordedAt: 'asc' },
      take: 2_000,
    });
    return rows.map((r) => ({ latitude: r.latitude, longitude: r.longitude, accuracyM: r.accuracyM, recordedAt: r.recordedAt }));
  }

  async deleteHistory(userId: string, before?: Date) {
    const r = await this.prisma.locationHistory.deleteMany({ where: { userId, ...(before ? { recordedAt: { lt: before } } : {}) } });
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'location.history_delete', meta: { count: r.count } } });
    return { deleted: r.count };
  }

  /** Deletes history past each user's own retention, capped by the server maximum. */
  @Cron(CronExpression.EVERY_HOUR)
  async enforceRetention() {
    const max = this.env.locationHistoryRetentionDays;
    const globalCut = new Date(Date.now() - max * 86_400_000);
    const g = await this.prisma.locationHistory.deleteMany({ where: { recordedAt: { lt: globalCut } } });
    const shorter = await this.prisma.userPrivacySettings.findMany({ where: { historyRetentionDays: { lt: max } }, select: { userId: true, historyRetentionDays: true } });
    let n = g.count;
    for (const p of shorter) {
      const r = await this.prisma.locationHistory.deleteMany({ where: { userId: p.userId, recordedAt: { lt: new Date(Date.now() - p.historyRetentionDays * 86_400_000) } } });
      n += r.count;
    }
    // History is only kept for people who still want it.
    const off = await this.prisma.locationHistory.deleteMany({ where: { user: { privacy: { historyEnabled: false } } } });
    n += off.count;
    if (n) this.log.log(`retention removed ${n} history row(s)`);
    return n;
  }
}
