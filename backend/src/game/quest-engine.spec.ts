import { applyEvent, QuestDef, QuestState } from './quest-engine';

const s = (progress = 0, keys: string[] = [], circleId: string | null = null): QuestState => ({ progress, progressKeys: keys, circleId });

describe('quest engine', () => {
  const tiles: QuestDef = { condition: 'DISCOVER_TILES', target: 2, params: null };

  it('counts each tile once, so repeated samples add nothing', () => {
    const a = applyEvent(tiles, s(), { kind: 'TILE', tileId: 't1' })!;
    expect(a).toMatchObject({ progress: 1, delta: 1, completed: false });
    expect(applyEvent(tiles, s(1, ['t1']), { kind: 'TILE', tileId: 't1' })).toBeNull();
    expect(applyEvent(tiles, s(1, ['t1']), { kind: 'TILE', tileId: 't2' })).toMatchObject({ progress: 2, completed: true });
  });

  it('stops at the target', () => {
    expect(applyEvent(tiles, s(2, ['a', 'b']), { kind: 'TILE', tileId: 'c' })).toBeNull();
  });

  it('limits landmark quests to listed landmarks when given', () => {
    const def: QuestDef = { condition: 'VISIT_LANDMARKS', target: 1, params: { landmarkIds: ['L1'] } };
    expect(applyEvent(def, s(), { kind: 'LANDMARK', landmarkId: 'L2', setCode: null })).toBeNull();
    expect(applyEvent(def, s(), { kind: 'LANDMARK', landmarkId: 'L1', setCode: null })?.completed).toBe(true);
  });

  it('collects a landmark set only from that set', () => {
    const def: QuestDef = { condition: 'COLLECT_LANDMARK_SET', target: 2, params: { setCode: 'jkt' } };
    expect(applyEvent(def, s(), { kind: 'LANDMARK', landmarkId: 'x', setCode: 'other' })).toBeNull();
    expect(applyEvent(def, s(), { kind: 'LANDMARK', landmarkId: 'x', setCode: 'jkt' })?.progress).toBe(1);
  });

  it('adds walking distance and ignores a replayed sample', () => {
    const def: QuestDef = { condition: 'WALK_DISTANCE', target: 1000, params: null };
    const a = applyEvent(def, s(), { kind: 'DISTANCE', meters: 420.4, sampleRef: 'r1' })!;
    expect(a.progress).toBe(420);
    expect(applyEvent(def, s(a.progress, a.progressKeys), { kind: 'DISTANCE', meters: 420, sampleRef: 'r1' })).toBeNull();
    expect(applyEvent(def, s(900, ['r1']), { kind: 'DISTANCE', meters: 300, sampleRef: 'r2' })).toMatchObject({ progress: 1000, completed: true });
  });

  it('counts circle tiles only for the quest circle', () => {
    const def: QuestDef = { condition: 'CIRCLE_TILES', target: 5, params: null };
    expect(applyEvent(def, s(0, [], 'c1'), { kind: 'CIRCLE_TILE', circleId: 'c2', tileId: 't' })).toBeNull();
    expect(applyEvent(def, s(0, [], 'c1'), { kind: 'CIRCLE_TILE', circleId: 'c1', tileId: 't' })?.progress).toBe(1);
  });

  it('counts distinct active days', () => {
    const def: QuestDef = { condition: 'ACTIVE_DAYS', target: 4, params: null };
    expect(applyEvent(def, s(1, ['2026-10-27']), { kind: 'ACTIVE_DAY', dayKey: '2026-10-27' })).toBeNull();
    expect(applyEvent(def, s(1, ['2026-10-27']), { kind: 'ACTIVE_DAY', dayKey: '2026-10-28' })?.progress).toBe(2);
  });
});
