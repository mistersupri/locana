/**
 * Side effects collected inside a game transaction and delivered only after it commits,
 * so a rolled-back transaction never produces a notification or realtime event.
 */
export interface GameEffects {
  xpGained: number;
  newTiles: string[];
  landmarks: { id: string; name: string; firstVisit: boolean }[];
  levelUps: { from: number; to: number }[];
  achievements: { code: string; name: string; rarity: string; rewardXp: number }[];
  collectibles: { code: string; name: string }[];
  questProgress: Map<string, { userId: string; userQuestId: string; questName: string; progress: number; target: number; completed: boolean }>;
  territoryChanges: { territoryId: string; name: string; ownerCircleId: string | null; previousOwnerCircleId: string | null }[];
  challengeCompletions: { circleId: string; title: string }[];
}

export const emptyEffects = (): GameEffects => ({
  xpGained: 0,
  newTiles: [],
  landmarks: [],
  levelUps: [],
  achievements: [],
  collectibles: [],
  questProgress: new Map(),
  territoryChanges: [],
  challengeCompletions: [],
});
