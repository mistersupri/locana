/**
 * Quest engine: reusable conditions evaluated against game events.
 *
 * Quests are data (QuestDefinition rows). This module knows how each condition consumes
 * events; it has no database access so the rules are easy to test. Distinct "keys"
 * (tile ids, landmark ids, day keys) make every count idempotent: a repeated GPS sample,
 * a retried upload or a replayed request can never add progress twice.
 */
export type QuestConditionName =
  | 'DISCOVER_TILES'
  | 'VISIT_LANDMARKS'
  | 'WALK_DISTANCE'
  | 'CIRCLE_TILES'
  | 'ACTIVE_DAYS'
  | 'COLLECT_LANDMARK_SET';

export type GameEvent =
  | { kind: 'TILE'; tileId: string }
  | { kind: 'CIRCLE_TILE'; circleId: string; tileId: string }
  | { kind: 'LANDMARK'; landmarkId: string; setCode: string | null }
  | { kind: 'DISTANCE'; meters: number; sampleRef: string }
  | { kind: 'ACTIVE_DAY'; dayKey: string };

export interface QuestDef {
  condition: QuestConditionName;
  target: number;
  params: { landmarkIds?: string[]; setCode?: string } | null;
}

export interface QuestState {
  progress: number;
  progressKeys: string[];
  circleId: string | null;
}

export interface QuestUpdate {
  progress: number;
  progressKeys: string[];
  delta: number;
  completed: boolean;
}

/** Distance progress keeps the last sample refs so a retried upload is not counted twice. */
const MAX_DISTANCE_KEYS = 200;

export function applyEvent(def: QuestDef, state: QuestState, e: GameEvent): QuestUpdate | null {
  if (state.progress >= def.target) return null;
  const keys = new Set(state.progressKeys);
  const addKey = (k: string): QuestUpdate | null => {
    if (keys.has(k)) return null;
    const progress = Math.min(def.target, state.progress + 1);
    return { progress, progressKeys: [...state.progressKeys, k], delta: progress - state.progress, completed: progress >= def.target };
  };

  switch (def.condition) {
    case 'DISCOVER_TILES':
      return e.kind === 'TILE' ? addKey(e.tileId) : null;
    case 'VISIT_LANDMARKS': {
      if (e.kind !== 'LANDMARK') return null;
      const allowed = def.params?.landmarkIds;
      if (allowed && allowed.length && !allowed.includes(e.landmarkId)) return null;
      return addKey(e.landmarkId);
    }
    case 'COLLECT_LANDMARK_SET':
      return e.kind === 'LANDMARK' && def.params?.setCode && e.setCode === def.params.setCode ? addKey(e.landmarkId) : null;
    case 'CIRCLE_TILES':
      return e.kind === 'CIRCLE_TILE' && state.circleId === e.circleId ? addKey(e.tileId) : null;
    case 'ACTIVE_DAYS':
      return e.kind === 'ACTIVE_DAY' ? addKey(e.dayKey) : null;
    case 'WALK_DISTANCE': {
      if (e.kind !== 'DISTANCE' || e.meters <= 0 || keys.has(e.sampleRef)) return null;
      const progress = Math.min(def.target, state.progress + Math.round(e.meters));
      if (progress === state.progress) return null;
      const progressKeys = [...state.progressKeys, e.sampleRef].slice(-MAX_DISTANCE_KEYS);
      return { progress, progressKeys, delta: progress - state.progress, completed: progress >= def.target };
    }
    default:
      return null;
  }
}

/** Unit shown next to progress numbers in the app. */
export function progressUnit(condition: QuestConditionName): 'tiles' | 'landmarks' | 'meters' | 'days' {
  switch (condition) {
    case 'WALK_DISTANCE':
      return 'meters';
    case 'ACTIVE_DAYS':
      return 'days';
    case 'VISIT_LANDMARKS':
    case 'COLLECT_LANDMARK_SET':
      return 'landmarks';
    default:
      return 'tiles';
  }
}
