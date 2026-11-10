/**
 * What can be met on the road and what can be bought to fight it. Numbers are balanced so that the free basic
 * attack beats the first monsters slowly, a bought weapon makes fights quick, and the strongest monsters need a
 * strong weapon (which in turn needs a level and coins, so XP matters even though it is never spent).
 */
export interface MonsterDef {
  code: string;
  name: string;
  emoji: string;
  maxHp: number;
  /** Damage dealt to the player per turn is around this value. */
  attack: number;
  coins: number;
  xp: number;
  minLevel: number;
}

export const MONSTERS: MonsterDef[] = [
  { code: 'slime', name: 'Road Slime', emoji: '🟢', maxHp: 30, attack: 5, coins: 10, xp: 15, minLevel: 1 },
  { code: 'bat', name: 'Night Bat', emoji: '🦇', maxHp: 45, attack: 7, coins: 14, xp: 22, minLevel: 1 },
  { code: 'goblin', name: 'Pothole Goblin', emoji: '👺', maxHp: 70, attack: 10, coins: 22, xp: 35, minLevel: 3 },
  { code: 'ghost', name: 'Traffic Ghost', emoji: '👻', maxHp: 95, attack: 12, coins: 30, xp: 50, minLevel: 5 },
  { code: 'golem', name: 'Asphalt Golem', emoji: '🗿', maxHp: 160, attack: 16, coins: 55, xp: 90, minLevel: 8 },
  { code: 'dragon', name: 'Jam Dragon', emoji: '🐉', maxHp: 320, attack: 24, coins: 120, xp: 220, minLevel: 12 },
];

export interface WeaponDef {
  code: string;
  name: string;
  emoji: string;
  power: number;
  costCoins: number;
  minLevel: number;
}

/** Bare hands: always available, never stored. */
export const BASIC_ATTACK: WeaponDef = { code: 'fists', name: 'Basic attack', emoji: '👊', power: 8, costCoins: 0, minLevel: 1 };

export const WEAPONS: WeaponDef[] = [
  { code: 'wooden_sword', name: 'Wooden Sword', emoji: '🗡️', power: 14, costCoins: 40, minLevel: 1 },
  { code: 'slingshot', name: 'Slingshot', emoji: '🏹', power: 18, costCoins: 100, minLevel: 2 },
  { code: 'iron_blade', name: 'Iron Blade', emoji: '⚔️', power: 26, costCoins: 250, minLevel: 4 },
  { code: 'magic_wand', name: 'Magic Wand', emoji: '🔮', power: 34, costCoins: 500, minLevel: 6 },
  { code: 'thunder_hammer', name: 'Thunder Hammer', emoji: '🔨', power: 48, costCoins: 1000, minLevel: 9 },
  { code: 'dragon_claw', name: 'Dragon Claw', emoji: '🐲', power: 70, costCoins: 2200, minLevel: 13 },
];

export const monsterDef = (code: string) => MONSTERS.find((m) => m.code === code);
export const weaponDef = (code: string) => (code === BASIC_ATTACK.code ? BASIC_ATTACK : WEAPONS.find((w) => w.code === code));
