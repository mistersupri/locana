import { isOnFootSpeed, validateSample } from './location-validation';

const now = new Date('2026-10-28T10:00:00Z');
const at = (sec: number) => new Date(now.getTime() + sec * 1000);
const base = { latitude: -6.2, longitude: 106.8, accuracyM: 10, recordedAt: at(-60), sequence: 1, source: 'fused' };

describe('validateSample', () => {
  it('accepts and trusts a precise first sample', () => {
    const v = validateSample(base, null, now);
    expect(v).toMatchObject({ accepted: true, trusted: true });
  });

  it('rejects invalid coordinates and accuracy', () => {
    expect(validateSample({ ...base, latitude: 120 }, null, now).rejection).toBe('INVALID_COORDINATE');
    expect(validateSample({ ...base, accuracyM: 0 }, null, now).rejection).toBe('INVALID_ACCURACY');
  });

  it('rejects timestamps from the future and flags clock skew', () => {
    const v = validateSample({ ...base, recordedAt: at(600) }, null, now);
    expect(v.rejection).toBe('FUTURE_TIMESTAMP');
    expect(v.risks[0].kind).toBe('CLOCK_SKEW');
  });

  it('drops samples older than the latest one so stale updates never overwrite newer ones', () => {
    const prev = { ...base, recordedAt: at(-30) };
    expect(validateSample({ ...base, recordedAt: at(-40) }, prev, now).rejection).toBe('STALE_SEQUENCE');
    expect(validateSample({ ...base, recordedAt: at(-30) }, prev, now).rejection).toBe('STALE_SEQUENCE');
  });

  it('accepts but does not trust imprecise fixes (no reward, no accusation)', () => {
    const v = validateSample({ ...base, accuracyM: 120 }, null, now);
    expect(v.accepted).toBe(true);
    expect(v.trusted).toBe(false);
    expect(v.risks).toHaveLength(0);
  });

  it('flags impossible travel without rejecting the sample', () => {
    const prev = { ...base, recordedAt: at(-60) };
    // ~55 km in 60 s
    const v = validateSample({ ...base, latitude: -5.7, recordedAt: at(0) }, prev, now);
    expect(v.accepted).toBe(true);
    expect(v.trusted).toBe(false);
    expect(v.risks.map((r) => r.kind)).toContain('IMPOSSIBLE_SPEED');
  });

  it('does not count jitter inside the accuracy circle as distance', () => {
    const prev = { ...base, accuracyM: 15, recordedAt: at(-30) };
    const v = validateSample({ ...base, latitude: -6.20005, accuracyM: 15, recordedAt: at(0) }, prev, now);
    expect(v.distanceFromPreviousM).toBe(0);
  });

  it('marks mock providers untrusted', () => {
    const v = validateSample({ ...base, isMock: true }, null, now);
    expect(v.trusted).toBe(false);
    expect(v.risks.map((r) => r.kind)).toContain('MOCK_PROVIDER');
  });

  it('knows walking speed from vehicle speed', () => {
    expect(isOnFootSpeed(1.4)).toBe(true);
    expect(isOnFootSpeed(12)).toBe(false);
    expect(isOnFootSpeed(null)).toBe(false);
  });
});
