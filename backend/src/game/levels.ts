/** Level curve helpers. The thresholds themselves live in the LevelThreshold table. */
export interface Threshold {
  level: number;
  totalXp: number;
}

export interface LevelInfo {
  level: number;
  totalXp: number;
  levelStartXp: number;
  nextLevelXp: number | null;
  progress: number; // 0..1 within the current level
}

export function levelFor(totalXp: number, thresholds: Threshold[]): LevelInfo {
  const sorted = [...thresholds].sort((a, b) => a.level - b.level);
  if (sorted.length === 0 || sorted[0].totalXp !== 0) throw new Error('Level curve must start with a 0 XP level');
  let current = sorted[0];
  let next: Threshold | null = null;
  for (let i = 0; i < sorted.length; i++) {
    if (totalXp >= sorted[i].totalXp) {
      current = sorted[i];
      next = sorted[i + 1] ?? null;
    }
  }
  const span = next ? next.totalXp - current.totalXp : 0;
  return {
    level: current.level,
    totalXp,
    levelStartXp: current.totalXp,
    nextLevelXp: next?.totalXp ?? null,
    progress: next ? Math.min(1, (totalXp - current.totalXp) / span) : 1,
  };
}

/** Default curve used by the seed: 0, 100, 250, 450, 700 then +300, +350, … */
export function defaultCurve(maxLevel = 50): Threshold[] {
  const out: Threshold[] = [
    { level: 1, totalXp: 0 },
    { level: 2, totalXp: 100 },
    { level: 3, totalXp: 250 },
    { level: 4, totalXp: 450 },
    { level: 5, totalXp: 700 },
  ];
  let step = 300;
  while (out.length < maxLevel) {
    const last = out[out.length - 1];
    out.push({ level: last.level + 1, totalXp: last.totalXp + step });
    step += 50;
  }
  return out;
}
