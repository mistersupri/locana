/** Period keys used by quests and leaderboards. All periods are computed in UTC+7 (WIB) by default. */
export const GAME_TZ_OFFSET_MIN = Number(process.env.GAME_TZ_OFFSET_MINUTES ?? 420);

function shifted(d: Date): Date {
  return new Date(d.getTime() + GAME_TZ_OFFSET_MIN * 60_000);
}

export function dayKey(d: Date = new Date()): string {
  return shifted(d).toISOString().slice(0, 10);
}

/** ISO-8601 week key, e.g. 2026-W44. */
export function weekKey(d: Date = new Date()): string {
  const s = shifted(d);
  const date = new Date(Date.UTC(s.getUTCFullYear(), s.getUTCMonth(), s.getUTCDate()));
  const dayNum = date.getUTCDay() || 7;
  date.setUTCDate(date.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((date.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7);
  return `${date.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}

/** Season = calendar quarter, e.g. 2026-Q4. */
export function seasonKey(d: Date = new Date()): string {
  const s = shifted(d);
  return `${s.getUTCFullYear()}-Q${Math.floor(s.getUTCMonth() / 3) + 1}`;
}

/** UTC instant at which the local (game-timezone) day containing `d` ends. */
export function endOfDay(d: Date = new Date()): Date {
  const s = shifted(d);
  const localMidnight = Date.UTC(s.getUTCFullYear(), s.getUTCMonth(), s.getUTCDate() + 1);
  return new Date(localMidnight - GAME_TZ_OFFSET_MIN * 60_000);
}

/** UTC instant at which the local ISO week (Mon–Sun) containing `d` ends. */
export function endOfWeek(d: Date = new Date()): Date {
  const s = shifted(d);
  const dow = s.getUTCDay() || 7;
  const localMidnight = Date.UTC(s.getUTCFullYear(), s.getUTCMonth(), s.getUTCDate() + (8 - dow));
  return new Date(localMidnight - GAME_TZ_OFFSET_MIN * 60_000);
}

export function startOfWeek(d: Date = new Date()): Date {
  return new Date(endOfWeek(d).getTime() - 7 * 86_400_000);
}

export function isWeekend(d: Date = new Date()): boolean {
  const dow = shifted(d).getUTCDay();
  return dow === 0 || dow === 6;
}
