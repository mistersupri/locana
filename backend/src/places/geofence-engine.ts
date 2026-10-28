import { haversineM } from '../common/geo';

/**
 * Geofence state machine with accuracy gating, hysteresis, confirmation and cooldown.
 *
 *  - Samples whose accuracy is worse than max(100 m, radius) are ignored entirely.
 *  - "Inside" means distance <= radius.
 *  - "Outside" means distance > radius + hysteresis where hysteresis = max(50 m, accuracy).
 *    Anything in between keeps the current state, so jitter at the boundary does nothing.
 *  - A transition needs two consecutive agreeing samples at least `confirmMs` apart
 *    (a single strong sample with accuracy < radius/2 and well inside/outside also confirms).
 *  - The same event type for the same user+place is suppressed within `cooldownMs`, and any
 *    event within `minGapMs` of the previous one is suppressed, so a flapping boundary sends
 *    one alert, not a burst. The state still follows the person; only the alert is dropped.
 */
export interface FenceInput {
  centerLat: number;
  centerLon: number;
  radiusM: number;
}

export interface FenceState {
  inside: boolean | null; // null = unknown (first observation)
  pendingInside: boolean | null;
  pendingSince: Date | null;
  lastEventType: 'ARRIVE' | 'LEAVE' | null;
  lastEventAt: Date | null;
}

export interface FenceSample {
  lat: number;
  lon: number;
  accuracyM: number;
  at: Date;
}

export interface FenceDecision {
  next: Pick<FenceState, 'inside' | 'pendingInside' | 'pendingSince'>;
  event: 'ARRIVE' | 'LEAVE' | null;
}

export const GEOFENCE_DEFAULTS = { confirmMs: 30_000, cooldownMs: 5 * 60_000, minGapMs: 90_000, minHysteresisM: 50 };

export function evaluateGeofence(
  fence: FenceInput,
  state: FenceState,
  s: FenceSample,
  opts = GEOFENCE_DEFAULTS,
): FenceDecision {
  const keep: FenceDecision = { next: { inside: state.inside, pendingInside: state.pendingInside, pendingSince: state.pendingSince }, event: null };
  if (s.accuracyM > Math.max(100, fence.radiusM)) return keep;

  const d = haversineM(fence.centerLat, fence.centerLon, s.lat, s.lon);
  const hysteresis = Math.max(opts.minHysteresisM, s.accuracyM);
  let observed: boolean | null = null;
  if (d <= fence.radiusM) observed = true;
  else if (d > fence.radiusM + hysteresis) observed = false;
  if (observed === null) return { next: { ...keep.next, pendingInside: null, pendingSince: null }, event: null };

  // First observation establishes a baseline without emitting an event.
  if (state.inside === null) return { next: { inside: observed, pendingInside: null, pendingSince: null }, event: null };
  if (observed === state.inside) return { next: { inside: state.inside, pendingInside: null, pendingSince: null }, event: null };

  const strong = s.accuracyM <= fence.radiusM / 2 && (observed ? d <= fence.radiusM - s.accuracyM : d > fence.radiusM + 2 * hysteresis);
  const confirmed =
    strong ||
    (state.pendingInside === observed && state.pendingSince !== null && s.at.getTime() - state.pendingSince.getTime() >= opts.confirmMs);
  if (!confirmed) {
    const pendingSince = state.pendingInside === observed && state.pendingSince ? state.pendingSince : s.at;
    return { next: { inside: state.inside, pendingInside: observed, pendingSince }, event: null };
  }

  const type = observed ? 'ARRIVE' : 'LEAVE';
  const sinceLast = state.lastEventAt ? s.at.getTime() - state.lastEventAt.getTime() : Infinity;
  const suppressed = sinceLast < opts.minGapMs || (state.lastEventType === type && sinceLast < opts.cooldownMs);
  return { next: { inside: observed, pendingInside: null, pendingSince: null }, event: suppressed ? null : type };
}
