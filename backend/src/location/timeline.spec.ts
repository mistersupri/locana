import { buildTimeline, currentMovement, TimelinePoint } from './timeline';

const T0 = Date.UTC(2026, 10, 8, 0, 0, 0);
// 0.001 degrees of latitude is about 111 m.
const pt = (min: number, dLat = 0, extra: Partial<TimelinePoint> = {}): TimelinePoint => ({
  latitude: -6.2 + dLat,
  longitude: 106.8,
  accuracyM: 10,
  recordedAt: new Date(T0 + min * 60_000),
  ...extra,
});

describe('buildTimeline', () => {
  it('returns nothing for no data', () => expect(buildTimeline([])).toEqual([]));

  it('finds a stay when the person remains within 100 m for 5 minutes', () => {
    const segs = buildTimeline([pt(0), pt(2, 0.0002), pt(5, 0.0001), pt(9)]);
    expect(segs).toHaveLength(1);
    expect(segs[0]).toMatchObject({ type: 'STAY', durationSec: 9 * 60 });
  });

  it('does not call a short pause a stay', () => {
    const segs = buildTimeline([pt(0), pt(2), pt(3, 0.01)]);
    expect(segs.every((s) => s.type !== 'STAY')).toBe(true);
  });

  it('splits stay, trip, stay', () => {
    const points = [pt(0), pt(6), pt(10, 0.004), pt(13, 0.012), pt(16, 0.02), pt(20, 0.02), pt(26, 0.0201)];
    const segs = buildTimeline(points);
    expect(segs.map((s) => s.type)).toEqual(['STAY', 'TRIP', 'STAY']);
    const trip = segs[1];
    if (trip.type !== 'TRIP') throw new Error('expected trip');
    expect(trip.distanceM).toBeGreaterThan(2000);
    expect(trip.mode).toBe('DRIVE');
    expect(trip.path.length).toBeGreaterThanOrEqual(2);
  });

  it('calls slow movement a walk', () => {
    // about 4 km/h: 111 m per minute is 6.7 km/h, so use 0.0005 degrees (55 m) per minute
    const points = Array.from({ length: 12 }, (_, i) => pt(i, i * 0.0005));
    const segs = buildTimeline(points);
    const trip = segs.find((s) => s.type === 'TRIP');
    expect(trip && trip.type === 'TRIP' && trip.mode).toBe('WALK');
  });

  it('ignores imprecise fixes', () => {
    const segs = buildTimeline([pt(0), pt(3, 0.05, { accuracyM: 900 }), pt(6)]);
    expect(segs).toHaveLength(1);
    expect(segs[0].type).toBe('STAY');
  });

  it('downsamples long paths', () => {
    const points = Array.from({ length: 600 }, (_, i) => pt(i / 6, i * 0.0003));
    const trip = buildTimeline(points).find((s) => s.type === 'TRIP');
    expect(trip && trip.type === 'TRIP' && trip.path.length).toBeLessThanOrEqual(121);
  });
});

describe('currentMovement', () => {
  it('reports how long the person has been staying', () => {
    const m = currentMovement(pt(30), [pt(0), pt(10, 0.0003), pt(20)]);
    expect(m.state).toBe('STAYING');
    expect(m.since).toEqual(new Date(T0));
  });

  it('only counts the unbroken run', () => {
    const m = currentMovement(pt(60), [pt(0, 0.05), pt(30, 0.05), pt(40), pt(50)]);
    expect(m.state).toBe('STAYING');
    expect(m.since).toEqual(new Date(T0 + 40 * 60_000));
  });

  it('is moving when the device reports speed', () => {
    expect(currentMovement(pt(5, 0, { speedMps: 8 }), [pt(4, 0.0003)]).state).toBe('MOVING');
  });

  it('is unknown without history', () => {
    expect(currentMovement(pt(5), [])).toEqual({ state: 'UNKNOWN', since: null });
  });
});
