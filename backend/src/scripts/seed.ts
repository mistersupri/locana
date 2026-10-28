/**
 * Game configuration seed. Safe to run repeatedly: everything is upserted by code.
 *
 * Landmarks: a small starter set of well-known public places in central Jakarta so the game is
 * playable in development. Coordinates are approximate centre points; verify each one (and
 * add your own city) through POST /v1/admin/landmarks before a public launch.
 *
 * Run: npm run seed (dev, ts-node) or node dist/scripts/seed.js (production image).
 */
import { PrismaClient, QuestCondition, QuestType, Rarity, CollectibleKind } from '@prisma/client';
import { defaultCurve } from '../game/levels';

const prisma = new PrismaClient();

const LEVEL_REWARDS: Record<number, string> = { 3: 'marker_compass', 5: 'frame_trailhead', 8: 'title_wayfinder', 10: 'frame_summit', 15: 'marker_lantern', 20: 'title_cartographer' };

const COLLECTIBLES: { code: string; name: string; description: string; kind: CollectibleKind; rarity: Rarity; setCode?: string; iconKey: string }[] = [
  { code: 'marker_compass', name: 'Compass marker', description: 'A map marker with a compass needle. Reach level 3.', kind: 'MAP_MARKER', rarity: 'COMMON', iconKey: 'marker_compass' },
  { code: 'marker_lantern', name: 'Lantern marker', description: 'A warm lantern for night walks. Reach level 15.', kind: 'MAP_MARKER', rarity: 'EPIC', iconKey: 'marker_lantern' },
  { code: 'frame_trailhead', name: 'Trailhead frame', description: 'Avatar frame for players who reached level 5.', kind: 'AVATAR_FRAME', rarity: 'RARE', iconKey: 'frame_trailhead' },
  { code: 'frame_summit', name: 'Summit frame', description: 'Avatar frame for players who reached level 10.', kind: 'AVATAR_FRAME', rarity: 'EPIC', iconKey: 'frame_summit' },
  { code: 'title_wayfinder', name: 'Wayfinder', description: 'Title shown under your name. Reach level 8.', kind: 'TITLE', rarity: 'RARE', iconKey: 'title' },
  { code: 'title_cartographer', name: 'Cartographer', description: 'Title shown under your name. Reach level 20.', kind: 'TITLE', rarity: 'LEGENDARY', iconKey: 'title' },
  { code: 'title_neighbor', name: 'Good Neighbor', description: 'Earned with the Neighborhood Explorer achievement.', kind: 'TITLE', rarity: 'RARE', iconKey: 'title' },
  { code: 'badge_team', name: 'Team badge', description: 'Earned by finishing a circle quest together.', kind: 'BADGE', rarity: 'RARE', iconKey: 'badge_team' },
  { code: 'badge_heritage', name: 'Heritage Walk badge', description: 'Collected every stamp of the Central Jakarta set.', kind: 'BADGE', rarity: 'EPIC', setCode: 'jakarta_center', iconKey: 'badge_heritage' },
];

const LANDMARKS = [
  { code: 'monas', name: 'Monumen Nasional', category: 'monument', latitude: -6.175392, longitude: 106.827153, radiusM: 150, description: 'National Monument in Merdeka Square.' },
  { code: 'istiqlal', name: 'Masjid Istiqlal', category: 'religious', latitude: -6.170166, longitude: 106.831375, radiusM: 120, description: 'National mosque of Indonesia. Visit respectfully outside prayer times.' },
  { code: 'katedral', name: 'Gereja Katedral Jakarta', category: 'religious', latitude: -6.169129, longitude: 106.833204, radiusM: 80, description: 'Neo-gothic cathedral facing Istiqlal.' },
  { code: 'museum_nasional', name: 'Museum Nasional Indonesia', category: 'museum', latitude: -6.1764, longitude: 106.8217, radiusM: 100, description: 'Museum of Indonesian history and culture.' },
  { code: 'fatahillah', name: 'Taman Fatahillah', category: 'square', latitude: -6.1352, longitude: 106.8133, radiusM: 120, description: 'Main square of the Kota Tua old town.' },
  { code: 'suropati', name: 'Taman Suropati', category: 'park', latitude: -6.1995, longitude: 106.8325, radiusM: 100, description: 'Shaded park in Menteng, often with music on weekends.' },
  { code: 'taman_menteng', name: 'Taman Menteng', category: 'park', latitude: -6.1967, longitude: 106.8293, radiusM: 100, description: 'Public park built on the former Persija stadium.' },
  { code: 'perpusnas', name: 'Perpustakaan Nasional', category: 'library', latitude: -6.1804, longitude: 106.8263, radiusM: 80, description: 'National Library of Indonesia.' },
  { code: 'bundaran_hi', name: 'Bundaran HI', category: 'square', latitude: -6.195, longitude: 106.823, radiusM: 100, description: 'Welcome Monument roundabout. Stay on the sidewalk.' },
  { code: 'gbk', name: 'Gelora Bung Karno', category: 'park', latitude: -6.2185, longitude: 106.8023, radiusM: 250, description: 'Sports complex with public jogging tracks.' },
];

const ACHIEVEMENTS = [
  { code: 'first_discovery', name: 'First Discovery', description: 'Reveal your first map tile.', rarity: 'COMMON', iconKey: 'ach_first', criteria: { metric: 'tilesDiscovered', gte: 1 }, rewardXp: 20 },
  { code: 'neighborhood_explorer', name: 'Neighborhood Explorer', description: 'Reveal 25 map tiles.', rarity: 'RARE', iconKey: 'ach_neighborhood', criteria: { metric: 'tilesDiscovered', gte: 25 }, rewardXp: 100, rewardCollectibleCode: 'title_neighbor' },
  { code: 'weekend_adventurer', name: 'Weekend Adventurer', description: 'Reveal 10 tiles on weekends.', rarity: 'RARE', iconKey: 'ach_weekend', criteria: { metric: 'weekendTiles', gte: 10 }, rewardXp: 80 },
  { code: 'landmark_collector', name: 'Landmark Collector', description: 'Visit 5 different landmarks.', rarity: 'EPIC', iconKey: 'ach_landmarks', criteria: { metric: 'landmarksVisited', gte: 5 }, rewardXp: 150 },
  { code: 'quest_streak', name: 'Quest Streak', description: 'Stay active 7 days in a row.', rarity: 'RARE', iconKey: 'ach_streak', criteria: { metric: 'streak', gte: 7 }, rewardXp: 120 },
  { code: 'team_explorer', name: 'Team Explorer', description: 'Finish a circle quest with your team.', rarity: 'RARE', iconKey: 'ach_team', criteria: { metric: 'circleQuests', gte: 1 }, rewardXp: 100, rewardCollectibleCode: 'badge_team' },
  { code: 'circle_champion', name: 'Circle Champion', description: 'Claim a territory for your circle.', rarity: 'EPIC', iconKey: 'ach_champion', criteria: { metric: 'territoriesClaimed', gte: 1 }, rewardXp: 150 },
  { code: 'map_completionist', name: 'Map Completionist', description: 'Reveal 500 map tiles.', rarity: 'LEGENDARY', iconKey: 'ach_completionist', criteria: { metric: 'tilesDiscovered', gte: 500 }, rewardXp: 500 },
  { code: 'quest_runner', name: 'Quest Runner', description: 'Claim 10 quests.', rarity: 'COMMON', iconKey: 'ach_quests', criteria: { metric: 'questsCompleted', gte: 10 }, rewardXp: 60 },
  { code: 'level_10', name: 'Seasoned', description: 'Reach level 10.', rarity: 'EPIC', iconKey: 'ach_level', criteria: { metric: 'level', gte: 10 }, rewardXp: 0 },
] as const;

const DAY = 86_400_000;

async function main() {
  for (const t of defaultCurve(50)) {
    await prisma.levelThreshold.upsert({
      where: { level: t.level },
      create: { ...t, rewardCollectibleCode: LEVEL_REWARDS[t.level] ?? null },
      update: { totalXp: t.totalXp, rewardCollectibleCode: LEVEL_REWARDS[t.level] ?? null },
    });
  }

  const stamps = LANDMARKS.map((l) => ({
    code: `stamp_${l.code}`,
    name: `${l.name} stamp`,
    description: `Visited ${l.name}.`,
    kind: 'LANDMARK_STAMP' as CollectibleKind,
    rarity: 'COMMON' as Rarity,
    setCode: 'jakarta_center',
    iconKey: `stamp_${l.category}`,
  }));
  for (const c of [...COLLECTIBLES, ...stamps]) await prisma.collectibleDefinition.upsert({ where: { code: c.code }, create: c, update: c });

  for (const l of LANDMARKS) {
    const data = { ...l, setCode: 'jakarta_center', collectibleCode: `stamp_${l.code}`, rewardXp: 50 };
    const lm = await prisma.landmark.upsert({ where: { code: l.code }, create: data, update: data });
    await prisma.territory.upsert({ where: { landmarkId: lm.id }, create: { landmarkId: lm.id, name: l.name }, update: { name: l.name } });
  }

  for (const [i, a] of ACHIEVEMENTS.entries()) {
    const data = { ...a, rarity: a.rarity as Rarity, criteria: a.criteria, sortOrder: i, rewardCollectibleCode: 'rewardCollectibleCode' in a ? a.rewardCollectibleCode : null };
    await prisma.achievementDefinition.upsert({ where: { code: a.code }, create: data, update: data });
  }

  const now = new Date();
  const quests: { code: string; name: string; description: string; type: QuestType; condition: QuestCondition; target: number; rewardXp: number; params?: object; minLevel?: number; startsAt?: Date; endsAt?: Date; rewardCollectibleCode?: string }[] = [
    { code: 'daily_tiles', name: 'Fresh ground', description: 'Reveal 3 map tiles you have never visited.', type: 'DAILY', condition: 'DISCOVER_TILES', target: 3, rewardXp: 30 },
    { code: 'daily_walk', name: 'Easy loop', description: 'Walk 1.5 km at your own pace. Driving does not count.', type: 'DAILY', condition: 'WALK_DISTANCE', target: 1500, rewardXp: 30 },
    { code: 'daily_landmark', name: 'Landmark check-in', description: 'Visit any public landmark.', type: 'DAILY', condition: 'VISIT_LANDMARKS', target: 1, rewardXp: 40 },
    { code: 'daily_circle', name: 'Team sweep', description: 'Your team circle reveals 5 tiles together.', type: 'DAILY', condition: 'CIRCLE_TILES', target: 5, rewardXp: 40 },
    { code: 'weekly_tiles', name: 'Map maker', description: 'Reveal 20 new map tiles this week.', type: 'WEEKLY', condition: 'DISCOVER_TILES', target: 20, rewardXp: 150 },
    { code: 'weekly_active', name: 'Steady pace', description: 'Be active on 4 different days this week.', type: 'WEEKLY', condition: 'ACTIVE_DAYS', target: 4, rewardXp: 120 },
    { code: 'weekly_landmarks', name: 'Three stops', description: 'Visit 3 different landmarks this week.', type: 'WEEKLY', condition: 'VISIT_LANDMARKS', target: 3, rewardXp: 150 },
    { code: 'weekly_circle', name: 'Circle expedition', description: 'Your team circle reveals 40 tiles this week.', type: 'WEEKLY', condition: 'CIRCLE_TILES', target: 40, rewardXp: 200 },
    {
      code: 'event_heritage_walk',
      name: 'Heritage walk',
      description: 'Collect all 10 stamps of the Central Jakarta landmark set.',
      type: 'SPECIAL',
      condition: 'COLLECT_LANDMARK_SET',
      target: LANDMARKS.length,
      rewardXp: 400,
      params: { setCode: 'jakarta_center' },
      startsAt: now,
      endsAt: new Date(now.getTime() + 60 * DAY),
      rewardCollectibleCode: 'badge_heritage',
    },
  ];
  for (const q of quests) {
    const existing = await prisma.questDefinition.findUnique({ where: { code: q.code } });
    // Keep an existing event window on re-seed so running the seed twice does not extend it.
    const data = { ...q, params: q.params ?? {}, startsAt: existing?.startsAt ?? q.startsAt, endsAt: existing?.endsAt ?? q.endsAt };
    await prisma.questDefinition.upsert({ where: { code: q.code }, create: data, update: data });
  }

  const season = await prisma.gameEvent.findUnique({ where: { code: 'season_1' } });
  if (!season) {
    await prisma.gameEvent.create({
      data: {
        code: 'season_1',
        name: 'Season 1',
        description: 'Weekly territory rounds around public landmarks. Points reset every Monday.',
        kind: 'TERRITORY_SEASON',
        startsAt: now,
        endsAt: new Date(now.getTime() + 90 * DAY),
      },
    });
  }
  console.log('seed complete');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
