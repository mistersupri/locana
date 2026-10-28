import { haversineM, isValidCoordinate } from '../common/geo';

/**
 * Location sample validation and probabilistic anti-cheat.
 *
 * A sample is either rejected (malformed, from the future, older than what we already have)
 * or accepted with a "trust" verdict. Only TRUSTED samples may advance game progress
 * (tiles, landmarks, distance, territories). SUSPECT samples are still usable for live
 * sharing (the person really is somewhere) but never earn rewards. Nothing here is treated
 * as proof of cheating; signals are stored with a weight for later review.
 */
export interface LocationSampleInput {
  latitude: number;
  longitude: number;
  accuracyM: number;
  recordedAt: Date;
  sequence: number;
  speedMps?: number | null;
  source: string;
  isMock?: boolean;
}

export interface PreviousSample {
  latitude: number;
  longitude: number;
  accuracyM: number;
  recordedAt: Date;
  sequence: number;
}

export type Rejection = 'INVALID_COORDINATE' | 'INVALID_ACCURACY' | 'FUTURE_TIMESTAMP' | 'TOO_OLD' | 'STALE_SEQUENCE';
export type RiskKind = 'IMPOSSIBLE_SPEED' | 'CLOCK_SKEW' | 'LOW_ACCURACY' | 'MOCK_PROVIDER';

export interface ValidationResult {
  accepted: boolean;
  rejection?: Rejection;
  /** true when the sample may count towards game rewards */
  trusted: boolean;
  risks: { kind: RiskKind; weight: number }[];
  /** metres moved since the previous accepted sample, when plausible */
  distanceFromPreviousM: number;
  /** implied speed from the previous sample (m/s) */
  impliedSpeedMps: number | null;
}

export const VALIDATION_LIMITS = {
  maxFutureSkewMs: 2 * 60_000,
  maxAgeMs: 24 * 60 * 60_000,
  maxAccuracyM: 5_000,
  rewardAccuracyM: 50,
  // ~ 250 km/h: faster than ground travel; flagged, not rejected (planes exist)
  impossibleSpeedMps: 70,
  // samples less than this far apart are "stationary jitter" and never add distance
  jitterFloorM: 10,
};

export function validateSample(s: LocationSampleInput, prev: PreviousSample | null, now: Date = new Date()): ValidationResult {
  const base: ValidationResult = { accepted: false, trusted: false, risks: [], distanceFromPreviousM: 0, impliedSpeedMps: null };
  if (!isValidCoordinate(s.latitude, s.longitude)) return { ...base, rejection: 'INVALID_COORDINATE' };
  if (!Number.isFinite(s.accuracyM) || s.accuracyM <= 0 || s.accuracyM > VALIDATION_LIMITS.maxAccuracyM) {
    return { ...base, rejection: 'INVALID_ACCURACY' };
  }
  const skew = s.recordedAt.getTime() - now.getTime();
  if (skew > VALIDATION_LIMITS.maxFutureSkewMs) return { ...base, rejection: 'FUTURE_TIMESTAMP', risks: [{ kind: 'CLOCK_SKEW', weight: 2 }] };
  if (-skew > VALIDATION_LIMITS.maxAgeMs) return { ...base, rejection: 'TOO_OLD' };

  if (prev) {
    // Sequence numbers restart after reinstall; timestamps break the tie.
    const olderTime = s.recordedAt.getTime() <= prev.recordedAt.getTime();
    if (olderTime) return { ...base, rejection: 'STALE_SEQUENCE' };
  }

  const risks: ValidationResult['risks'] = [];
  let trusted = true;
  if (s.isMock) {
    risks.push({ kind: 'MOCK_PROVIDER', weight: 3 });
    trusted = false;
  }
  if (s.accuracyM > VALIDATION_LIMITS.rewardAccuracyM) {
    // Not cheating — just not precise enough to reward.
    trusted = false;
  }

  let distance = 0;
  let implied: number | null = null;
  if (prev) {
    const d = haversineM(prev.latitude, prev.longitude, s.latitude, s.longitude);
    const dt = (s.recordedAt.getTime() - prev.recordedAt.getTime()) / 1000;
    // Subtract combined uncertainty so inaccurate fixes are not mistaken for teleportation.
    const effective = Math.max(0, d - (s.accuracyM + prev.accuracyM));
    implied = dt > 0 ? effective / dt : null;
    if (implied !== null && implied > VALIDATION_LIMITS.impossibleSpeedMps) {
      risks.push({ kind: 'IMPOSSIBLE_SPEED', weight: implied > 300 ? 3 : 1 });
      trusted = false;
    }
    distance = d >= Math.max(VALIDATION_LIMITS.jitterFloorM, s.accuracyM) ? d : 0;
  }
  return { accepted: true, trusted, risks, distanceFromPreviousM: distance, impliedSpeedMps: implied };
}

/** Speed range considered "walking or running" for walking quests (excludes vehicles). */
export function isOnFootSpeed(speedMps: number | null | undefined): boolean {
  return speedMps != null && speedMps >= 0.3 && speedMps <= 4.5;
}
