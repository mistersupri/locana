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

  it('ignores a device speed that its own step contradicts', () => {
    expect(currentMovement(pt(5, 0, { speedMps: 8 }), [pt(4, 0.0003)]).state).toBe('UNKNOWN'); // 8 m/s, but 33 m in a minute
  });

  it('ignores a noisy speed reading when the phone has not moved', () => {
    expect(currentMovement(pt(5, 0.00002, { speedMps: 3 }), [pt(4)]).state).toBe('UNKNOWN');
  });

  it('does not trust a speed from an imprecise fix', () => {
    expect(currentMovement(pt(5, 0.003, { speedMps: 9, accuracyM: 80 }), [pt(4)]).state).toBe('UNKNOWN');
  });

  it('moves when the step and the speed agree, even without a device speed', () => {
    const m = currentMovement(pt(5, 0.001), [pt(4)]); // about 111 m in a minute: 1.9 m/s
    expect(m).toMatchObject({ state: 'MOVING', mode: 'WALKING' });
  });

  it('is unknown without history', () => {
    expect(currentMovement(pt(5), [])).toEqual({ state: 'UNKNOWN', mode: null, since: null });
  });
});

describe('driving detection', () => {
  const drive = (min: number, km: number, extra: Partial<TimelinePoint> = {}) => pt(min, km * 0.009, { speedMps: 9, ...extra });

  it('calls a clear vehicle speed with real distance driving', () => {
    const m = currentMovement(drive(5, 3), [drive(1, 0), drive(2, 0.6), drive(3, 1.2), drive(4, 1.8)]);
    expect(m).toMatchObject({ state: 'MOVING', mode: 'DRIVING' });
  });

  it('keeps calling it driving while crawling in traffic or waiting at a light', () => {
    const m = currentMovement(pt(8, 5 * 0.009 + 0.0001, { speedMps: 0.4 }), [drive(1, 0), drive(2, 1), drive(3, 2), drive(4, 3), drive(5, 4), pt(6, 5 * 0.009, { speedMps: 0 }), pt(7, 5 * 0.009, { speedMps: 0.2 })]);
    expect(m).toMatchObject({ state: 'MOVING', mode: 'DRIVING' });
  });

  it('trusts the phone saying vehicle even at a walking pace', () => {
    const m = currentMovement(pt(5, 0.0006, { speedMps: 2, activity: 'vehicle' }), [pt(4, 0, { activity: 'vehicle' })]);
    expect(m).toMatchObject({ state: 'MOVING', mode: 'DRIVING' });
  });

  it('a phone that says still is not walking, whatever its speed reads', () => {
    expect(currentMovement(pt(5, 0.0004, { speedMps: 1.6, activity: 'still' }), [pt(4)]).state).toBe('UNKNOWN');
  });

  it('uses walking and cycling from the phone', () => {
    expect(currentMovement(pt(5, 0.0002, { activity: 'walking' }), [pt(4)])).toMatchObject({ state: 'MOVING', mode: 'WALKING' });
    expect(currentMovement(pt(5, 0.0006, { activity: 'cycling' }), [pt(4)])).toMatchObject({ state: 'MOVING', mode: 'CYCLING' });
  });

  it('labels a trip from the activity votes, then from top speed', () => {
    const slow = Array.from({ length: 8 }, (_, i) => pt(i, i * 0.0006, { activity: 'vehicle' }));
    const t1 = buildTimeline(slow).find((s) => s.type === 'TRIP');
    expect(t1 && t1.type === 'TRIP' && t1.mode).toBe('DRIVE');
    const crawl = Array.from({ length: 8 }, (_, i) => pt(i, i * 0.0006, { speedMps: i === 3 ? 9 : 0.8 }));
    const t2 = buildTimeline(crawl).find((s) => s.type === 'TRIP');
    expect(t2 && t2.type === 'TRIP' && t2.mode).toBe('DRIVE');
    const bike = Array.from({ length: 8 }, (_, i) => pt(i, i * 0.0006, { activity: 'cycling' }));
    const t3 = buildTimeline(bike).find((s) => s.type === 'TRIP');
    expect(t3 && t3.type === 'TRIP' && t3.mode).toBe('CYCLE');
  });
});
