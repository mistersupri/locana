import { dayKey, endOfDay, endOfWeek, isWeekend, weekKey } from './periods';

// Game time is WIB (UTC+7) by default.
describe('periods', () => {
  it('rolls the day over at midnight WIB, not UTC', () => {
    expect(dayKey(new Date('2026-10-28T16:59:00Z'))).toBe('2026-10-28');
    expect(dayKey(new Date('2026-10-28T17:00:00Z'))).toBe('2026-10-29');
  });

  it('computes ISO weeks', () => {
    expect(weekKey(new Date('2026-10-28T05:00:00Z'))).toBe('2026-W44');
    expect(weekKey(new Date('2026-01-01T05:00:00Z'))).toBe('2026-W01');
  });

  it('ends the day and week at local midnight', () => {
    expect(endOfDay(new Date('2026-10-28T05:00:00Z')).toISOString()).toBe('2026-10-28T17:00:00.000Z');
    expect(endOfWeek(new Date('2026-10-28T05:00:00Z')).toISOString()).toBe('2026-11-01T17:00:00.000Z');
  });

  it('knows weekends in local time', () => {
    expect(isWeekend(new Date('2026-10-31T05:00:00Z'))).toBe(true);
    expect(isWeekend(new Date('2026-10-30T18:00:00Z'))).toBe(true); // Saturday 01:00 WIB
    expect(isWeekend(new Date('2026-10-30T05:00:00Z'))).toBe(false);
  });
});
