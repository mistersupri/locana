import { defaultCurve, levelFor } from './levels';

describe('levels', () => {
  const curve = defaultCurve(10);

  it('matches the documented start of the curve', () => {
    expect(curve.slice(0, 5).map((t) => t.totalXp)).toEqual([0, 100, 250, 450, 700]);
  });

  it('computes level and progress inside the level', () => {
    expect(levelFor(0, curve)).toMatchObject({ level: 1, progress: 0 });
    expect(levelFor(175, curve)).toMatchObject({ level: 2, levelStartXp: 100, nextLevelXp: 250, progress: 0.5 });
    expect(levelFor(250, curve).level).toBe(3);
  });

  it('caps at the last level', () => {
    const top = levelFor(1_000_000, curve);
    expect(top.level).toBe(10);
    expect(top.nextLevelXp).toBeNull();
    expect(top.progress).toBe(1);
  });

  it('rejects a curve that does not start at zero', () => {
    expect(() => levelFor(5, [{ level: 1, totalXp: 10 }])).toThrow();
  });
});
