import { haversineM } from '../common/geo';

/**
 * Turns a person's location history into stays and trips.
 *
 * A stay is a stretch of at least STAY_MIN_MS spent within STAY_RADIUS_M of where it began.
 * Everything between two stays is a trip. Pure functions, no database: tested in timeline.spec.ts.
 */
export interface TimelinePoint {
  latitude: number;
  longitude: number;
  accuracyM: number;
  recordedAt: Date;
  speedMps?: number | null;
}

export const STAY_RADIUS_M = 100;
export const STAY_MIN_MS = 5 * 60_000;
/** Fixes less precise than this are ignored: they would invent movement. */
export const MAX_ACCURACY_M = 150;
const MAX_PATH_POINTS = 120;
/** Average speed (km/h) above which a trip counts as a vehicle trip rather than a walk. */
const VEHICLE_KMH = 9;
const MOVING_MIN_SPEED_MPS = 1.5;
const MOVING_MIN_STEP_M = 25;
const MOVING_MAX_ACCURACY = 30;
const MOVING_LOOKBACK_MS = 10 * 60_000;

export type TimelineMode = 'WALK' | 'DRIVE';

export interface StaySegment {
  type: 'STAY';
  startedAt: Date;
  endedAt: Date;
  durationSec: number;
  latitude: number;
  longitude: number;
}

export interface TripSegment {
  type: 'TRIP';
  startedAt: Date;
  endedAt: Date;
  durationSec: number;
  distanceM: number;
  avgSpeedKmh: number;
  maxSpeedKmh: number;
  mode: TimelineMode;
  path: [number, number][];
}

export type TimelineSegment = StaySegment | TripSegment;

const usable = (points: TimelinePoint[]) =>
  points.filter((p) => p.accuracyM <= MAX_ACCURACY_M).sort((a, b) => a.recordedAt.getTime() - b.recordedAt.getTime());

function centroid(ps: TimelinePoint[]) {
  return {
    latitude: ps.reduce((s, p) => s + p.latitude, 0) / ps.length,
    longitude: ps.reduce((s, p) => s + p.longitude, 0) / ps.length,
  };
}

function tripFrom(ps: TimelinePoint[]): TripSegment | null {
  if (ps.length < 2) return null;
  let dist = 0;
  let maxMps = 0;
  for (let i = 1; i < ps.length; i++) {
    const d = haversineM(ps[i - 1].latitude, ps[i - 1].longitude, ps[i].latitude, ps[i].longitude);
    dist += d;
    const dt = (ps[i].recordedAt.getTime() - ps[i - 1].recordedAt.getTime()) / 1000;
    const implied = dt > 0 ? d / dt : 0;
    // Prefer the device's own speed; fall back to the speed between two fixes.
    maxMps = Math.max(maxMps, ps[i].speedMps ?? implied);
  }
  const startedAt = ps[0].recordedAt;
  const endedAt = ps[ps.length - 1].recordedAt;
  const durationSec = Math.max(1, Math.round((endedAt.getTime() - startedAt.getTime()) / 1000));
  if (dist < 30) return null; // GPS jitter, not a trip
  const avgKmh = (dist / durationSec) * 3.6;
  const step = Math.max(1, Math.ceil(ps.length / MAX_PATH_POINTS));
  const path = ps.filter((_, i) => i % step === 0 || i === ps.length - 1).map((p) => [round(p.latitude), round(p.longitude)] as [number, number]);
  return {
    type: 'TRIP',
    startedAt,
    endedAt,
    durationSec,
    distanceM: Math.round(dist),
    avgSpeedKmh: Math.round(avgKmh * 10) / 10,
    maxSpeedKmh: Math.round(maxMps * 3.6),
    mode: avgKmh >= VEHICLE_KMH ? 'DRIVE' : 'WALK',
    path,
  };
}

const round = (n: number) => Math.round(n * 1e6) / 1e6;

export function buildTimeline(input: TimelinePoint[]): TimelineSegment[] {
  const points = usable(input);
  const out: TimelineSegment[] = [];
  let travel: TimelinePoint[] = [];
  const flushTrip = (closing?: TimelinePoint) => {
    if (closing) travel.push(closing);
    const t = tripFrom(travel);
    if (t) out.push(t);
    travel = [];
  };

  let i = 0;
  while (i < points.length) {
    // Grow a candidate stay from point i.
    let j = i;
    while (j + 1 < points.length && haversineM(points[i].latitude, points[i].longitude, points[j + 1].latitude, points[j + 1].longitude) <= STAY_RADIUS_M) j++;
    const span = points[j].recordedAt.getTime() - points[i].recordedAt.getTime();
    if (j > i && span >= STAY_MIN_MS) {
      const group = points.slice(i, j + 1);
      // A trip ends where the stay begins.
      flushTrip(group[0]);
      const c = centroid(group);
      out.push({
        type: 'STAY',
        startedAt: group[0].recordedAt,
        endedAt: group[group.length - 1].recordedAt,
        durationSec: Math.round(span / 1000),
        latitude: round(c.latitude),
        longitude: round(c.longitude),
      });
      // The next trip starts where the stay ends.
      travel = [group[group.length - 1]];
      i = j + 1;
    } else {
      travel.push(points[i]);
      i++;
    }
  }
  flushTrip();
  return out;
}

export interface CurrentMovement {
  state: 'STAYING' | 'MOVING' | 'UNKNOWN';
  /** When the person arrived at where they are now. Only set while staying. */
  since: Date | null;
}

/**
 * Where the person is right now relative to their recent history. `latest` is their newest fix;
 * `history` is anything before it (any order). Staying means every fix back to `since` is within
 * STAY_RADIUS_M of the newest one and the run lasts at least STAY_MIN_MS.
 */
export function currentMovement(latest: TimelinePoint, history: TimelinePoint[]): CurrentMovement {
  const earlier = usable(history).filter((p) => p.recordedAt.getTime() <= latest.recordedAt.getTime());
  let since = latest.recordedAt;
  for (let k = earlier.length - 1; k >= 0; k--) {
    const p = earlier[k];
    if (haversineM(latest.latitude, latest.longitude, p.latitude, p.longitude) > STAY_RADIUS_M) break;
    since = p.recordedAt;
  }
  const staying = latest.recordedAt.getTime() - since.getTime() >= STAY_MIN_MS;
  if (staying) return { state: 'STAYING', since };
  // Moving needs two things to agree: a real step since the previous fix, and a speed that fits it.
  // A noisy speed reading from a phone lying on a desk is not movement.
  if (latest.accuracyM > MOVING_MAX_ACCURACY || earlier.length === 0) return { state: 'UNKNOWN', since: null };
  const prev = earlier[earlier.length - 1];
  const dt = (latest.recordedAt.getTime() - prev.recordedAt.getTime()) / 1000;
  if (dt <= 0 || dt * 1000 > MOVING_LOOKBACK_MS) return { state: 'UNKNOWN', since: null };
  const step = haversineM(latest.latitude, latest.longitude, prev.latitude, prev.longitude);
  const speed = latest.speedMps ?? step / dt;
  return step >= MOVING_MIN_STEP_M && speed >= MOVING_MIN_SPEED_MPS ? { state: 'MOVING', since: null } : { state: 'UNKNOWN', since: null };
}
