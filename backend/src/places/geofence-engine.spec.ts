import { evaluateGeofence, FenceState } from './geofence-engine';

const fence = { centerLat: -6.2, centerLon: 106.8, radiusM: 150 };
const t0 = new Date('2026-10-28T10:00:00Z');
const at = (s: number) => new Date(t0.getTime() + s * 1000);
// ~111 m per 0.001 deg latitude
const point = (metersNorth: number, accuracyM: number, sec: number) => ({ lat: -6.2 + metersNorth / 111_000, lon: 106.8, accuracyM, at: at(sec) });
const empty: FenceState = { inside: null, pendingInside: null, pendingSince: null, lastEventType: null, lastEventAt: null };

function run(samples: ReturnType<typeof point>[], start: FenceState = empty) {
  let st = start;
  const events: string[] = [];
  for (const s of samples) {
    const d = evaluateGeofence(fence, st, s);
    st = { ...st, ...d.next };
    if (d.event) {
      events.push(d.event);
      st.lastEventType = d.event;
      st.lastEventAt = s.at;
    }
  }
  return { st, events };
}

describe('geofence engine', () => {
  it('sets a baseline on the first sample without an event', () => {
    expect(run([point(0, 20, 0)]).events).toEqual([]);
  });

  it('needs two agreeing samples 30 s apart for a weak arrival', () => {
    const r = run([point(600, 40, 0), point(100, 90, 60), point(100, 90, 70), point(100, 90, 100)]);
    expect(r.events).toEqual(['ARRIVE']);
  });

  it('accepts one strong, precise sample deep inside', () => {
    expect(run([point(600, 20, 0), point(10, 10, 60)]).events).toEqual(['ARRIVE']);
  });

  it('ignores jitter in the hysteresis band', () => {
    const r = run([point(0, 20, 0), point(170, 20, 60), point(140, 20, 120), point(175, 20, 180)]);
    expect(r.events).toEqual([]);
  });

  it('ignores samples less accurate than the fence', () => {
    expect(run([point(600, 20, 0), point(0, 400, 60), point(0, 400, 120)]).events).toEqual([]);
  });

  it('sends one alert for a flapping boundary', () => {
    const r = run([point(600, 20, 0), point(0, 10, 60), point(600, 10, 90), point(0, 10, 120)]);
    expect(r.events).toEqual(['ARRIVE']);
    expect(r.st.inside).toBe(true);
  });

  it('reports a real departure and return spaced beyond the gap', () => {
    const r = run([point(600, 20, 0), point(0, 10, 60), point(600, 10, 200), point(0, 10, 320)]);
    expect(r.events).toEqual(['ARRIVE', 'LEAVE', 'ARRIVE']);
  });

  it('suppresses a second arrival within the cooldown when the departure was dropped', () => {
    // LEAVE at 90 s is inside the gap (dropped), so the next ARRIVE repeats the last alert.
    const r = run([point(600, 20, 0), point(0, 10, 60), point(600, 10, 90), point(0, 10, 200), point(0, 10, 500)]);
    expect(r.events).toEqual(['ARRIVE']);
  });
});
