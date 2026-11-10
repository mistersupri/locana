import { HttpStatus, Inject, Injectable, Optional } from '@nestjs/common';
import { Encounter } from '@prisma/client';
import { ApiError, badRequest, notFound } from '../common/errors';
import { haversineM } from '../common/geo';
import { emptyEffects } from '../game/effects';
import { EffectsPublisher } from '../game/effects-publisher.service';
import { AchievementsService } from '../game/achievements.service';
import { XpService } from '../game/xp.service';
import { NotificationsService } from '../notifications/notifications.service';
import { PrismaService } from '../prisma/prisma.service';
import { BASIC_ATTACK, MONSTERS, MonsterDef, monsterDef, WEAPONS, weaponDef } from './catalog';

export const BATTLE_RULES = {
  /** A new monster needs this long since the last one, and this far travelled since where the last one appeared. */
  cooldownMs: 8 * 60_000,
  minTravelM: 250,
  chance: 0.35,
  lifetimeMs: 15 * 60_000,
  playerHp: 100,
  critChance: 0.12,
  firstVisitCoins: 15,
};

export const RANDOM = 'BATTLE_RANDOM';

@Injectable()
export class BattleService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly notifications: NotificationsService,
    private readonly xp: XpService,
    private readonly achievements: AchievementsService,
    private readonly publisher: EffectsPublisher,
    @Optional() @Inject(RANDOM) private readonly injectedRandom?: () => number,
  ) {}

  /** Math.random unless a test injected a fixed source. Looked up each time so it can be stubbed. */
  private random(): number {
    return this.injectedRandom ? this.injectedRandom() : Math.random();
  }

  private view(e: Encounter) {
    const m = monsterDef(e.monsterCode)!;
    return {
      id: e.id,
      status: e.status,
      monster: { code: m.code, name: m.name, emoji: m.emoji },
      hp: e.hp,
      maxHp: e.maxHp,
      playerHp: e.playerHp,
      maxPlayerHp: BATTLE_RULES.playerHp,
      rewardCoins: e.rewardCoins,
      rewardXp: e.rewardXp,
      expiresAt: e.expiresAt,
    };
  }

  // ── meeting monsters ──
  /**
   * Called after an upload: with some luck, a monster steps onto the road. Only for people who play (exploration on),
   * only while they have travelled a bit, and never while another monster is waiting.
   */
  async maybeSpawn(userId: string, latest: { latitude: number; longitude: number; accuracyM: number }, now = new Date()): Promise<Encounter | null> {
    const privacy = await this.prisma.userPrivacySettings.findUnique({ where: { userId } });
    if (!privacy?.explorationEnabled || latest.accuracyM > 100) return null;
    await this.expireOld(userId, now);
    const last = await this.prisma.encounter.findFirst({ where: { userId }, orderBy: { createdAt: 'desc' } });
    if (last) {
      if (last.status === 'ACTIVE') return null;
      if (now.getTime() - last.createdAt.getTime() < BATTLE_RULES.cooldownMs) return null;
      if (haversineM(last.latitude, last.longitude, latest.latitude, latest.longitude) < BATTLE_RULES.minTravelM) return null;
    }
    if (this.random() >= BATTLE_RULES.chance) return null;
    return this.spawn(userId, latest, now);
  }

  /** Creates the encounter, skipping the luck and cooldown. Used by the roll above and by tests and the dev endpoint. */
  async spawn(userId: string, at: { latitude: number; longitude: number }, now = new Date(), code?: string): Promise<Encounter> {
    const profile = await this.prisma.userProfile.findUniqueOrThrow({ where: { userId } });
    const pool = MONSTERS.filter((m) => m.minLevel <= profile.level);
    const picked: MonsterDef = (code && monsterDef(code)) || pool[Math.min(pool.length - 1, Math.floor(Math.pow(this.random(), 1.6) * pool.length))];
    const e = await this.prisma.encounter.create({
      data: {
        userId,
        monsterCode: picked.code,
        hp: picked.maxHp,
        maxHp: picked.maxHp,
        playerHp: BATTLE_RULES.playerHp,
        rewardCoins: picked.coins,
        rewardXp: picked.xp,
        latitude: at.latitude,
        longitude: at.longitude,
        expiresAt: new Date(now.getTime() + BATTLE_RULES.lifetimeMs),
      },
    });
    await this.notifications.notify(userId, 'MONSTER', `A wild ${picked.name} appeared! ${picked.emoji}`, 'Fight it within 15 minutes, or let it go.', { encounterId: e.id, monster: picked.code });
    return e;
  }

  private async expireOld(userId: string, now: Date) {
    await this.prisma.encounter.updateMany({ where: { userId, status: 'ACTIVE', expiresAt: { lt: now } }, data: { status: 'EXPIRED', endedAt: now } });
  }

  async active(userId: string, now = new Date()) {
    await this.expireOld(userId, now);
    const e = await this.prisma.encounter.findFirst({ where: { userId, status: 'ACTIVE' }, orderBy: { createdAt: 'desc' } });
    const [profile, owned] = await Promise.all([
      this.prisma.userProfile.findUniqueOrThrow({ where: { userId } }),
      this.prisma.userWeapon.findMany({ where: { userId } }),
    ]);
    return {
      encounter: e ? this.view(e) : null,
      coins: profile.coins,
      weapons: [BASIC_ATTACK, ...WEAPONS.filter((w) => owned.some((o) => o.code === w.code))].map((w) => ({ code: w.code, name: w.name, emoji: w.emoji, power: w.power })),
    };
  }

  // ── fighting ──
  async attack(userId: string, id: string, weaponCode?: string) {
    const code = weaponCode ?? BASIC_ATTACK.code;
    const weapon = weaponDef(code);
    if (!weapon) throw badRequest('UNKNOWN_WEAPON', 'That weapon does not exist');
    if (weapon.code !== BASIC_ATTACK.code) {
      const owned = await this.prisma.userWeapon.findUnique({ where: { userId_code: { userId, code } } });
      if (!owned) throw new ApiError(HttpStatus.FORBIDDEN, 'WEAPON_NOT_OWNED', 'You do not own that weapon yet');
    }
    const fx = emptyEffects();
    const result = await this.prisma.$transaction(async (tx) => {
      // Lock the row so two fast taps are two sequential turns, not two turns on the same numbers.
      const rows = await tx.$queryRaw<{ id: string }[]>`SELECT "id" FROM "Encounter" WHERE "id" = ${id}::uuid AND "userId" = ${userId}::uuid FOR UPDATE`;
      if (rows.length === 0) throw notFound('Encounter');
      const e = await tx.encounter.findUniqueOrThrow({ where: { id } });
      if (e.status !== 'ACTIVE') throw new ApiError(HttpStatus.CONFLICT, 'ENCOUNTER_OVER', 'This fight is over');
      if (e.expiresAt < new Date()) {
        await tx.encounter.update({ where: { id }, data: { status: 'EXPIRED', endedAt: new Date() } });
        throw new ApiError(HttpStatus.CONFLICT, 'ENCOUNTER_OVER', 'The monster has gone');
      }
      const m = monsterDef(e.monsterCode)!;
      const crit = this.random() < BATTLE_RULES.critChance;
      const damage = Math.max(1, Math.round(weapon.power * (0.85 + this.random() * 0.3) * (crit ? 1.6 : 1)));
      const hp = Math.max(0, e.hp - damage);
      if (hp === 0) {
        await tx.encounter.update({ where: { id }, data: { hp: 0, status: 'WON', endedAt: new Date() } });
        await tx.userProfile.update({ where: { userId }, data: { coins: { increment: e.rewardCoins } } });
        await this.xp.grant(tx, userId, e.rewardXp, 'MONSTER', e.id, fx);
        await this.achievements.evaluate(tx, userId, fx);
        return { outcome: 'WON' as const, hit: { damage, crit }, counter: null, e: { ...e, hp: 0, status: 'WON' } };
      }
      const counter = Math.max(1, Math.round(m.attack * (0.7 + this.random() * 0.6)));
      const playerHp = Math.max(0, e.playerHp - counter);
      const lost = playerHp === 0;
      const next = await tx.encounter.update({ where: { id }, data: { hp, playerHp, ...(lost ? { status: 'LOST', endedAt: new Date() } : {}) } });
      return { outcome: lost ? ('LOST' as const) : ('CONTINUE' as const), hit: { damage, crit }, counter: { damage: counter }, e: next };
    });
    if (result.outcome === 'WON') await this.publisher.publish(userId, fx);
    const profile = await this.prisma.userProfile.findUniqueOrThrow({ where: { userId } });
    return {
      outcome: result.outcome,
      hit: result.hit,
      counter: result.counter,
      encounter: this.view(result.e as Encounter),
      coins: profile.coins,
      rewards: result.outcome === 'WON' ? { coins: result.e.rewardCoins, xp: result.e.rewardXp, levelUps: fx.levelUps, collectibles: fx.collectibles, achievements: fx.achievements } : null,
    };
  }

  async flee(userId: string, id: string) {
    const r = await this.prisma.encounter.updateMany({ where: { id, userId, status: 'ACTIVE' }, data: { status: 'FLED', endedAt: new Date() } });
    if (r.count === 0) throw new ApiError(HttpStatus.CONFLICT, 'ENCOUNTER_OVER', 'This fight is already over');
    return { fled: true };
  }

  // ── shop ──
  async shop(userId: string) {
    const [profile, owned] = await Promise.all([
      this.prisma.userProfile.findUniqueOrThrow({ where: { userId } }),
      this.prisma.userWeapon.findMany({ where: { userId } }),
    ]);
    return {
      coins: profile.coins,
      level: profile.level,
      weapons: [BASIC_ATTACK, ...WEAPONS].map((w) => ({
        ...w,
        owned: w.code === BASIC_ATTACK.code || owned.some((o) => o.code === w.code),
        affordable: profile.coins >= w.costCoins,
        unlocked: profile.level >= w.minLevel,
      })),
    };
  }

  async buy(userId: string, code: string) {
    const w = WEAPONS.find((x) => x.code === code);
    if (!w) throw badRequest('UNKNOWN_WEAPON', 'That weapon does not exist');
    await this.prisma.$transaction(async (tx) => {
      const profile = await tx.userProfile.findUniqueOrThrow({ where: { userId } });
      if (profile.level < w.minLevel) throw new ApiError(HttpStatus.CONFLICT, 'LEVEL_TOO_LOW', `Reach level ${w.minLevel} to buy the ${w.name}`);
      if (await tx.userWeapon.findUnique({ where: { userId_code: { userId, code } } })) throw new ApiError(HttpStatus.CONFLICT, 'ALREADY_OWNED', 'You already own this weapon');
      // Conditional decrement: two parallel purchases can never spend the same coins twice.
      const paid = await tx.userProfile.updateMany({ where: { userId, coins: { gte: w.costCoins } }, data: { coins: { decrement: w.costCoins } } });
      if (paid.count === 0) throw new ApiError(HttpStatus.CONFLICT, 'NOT_ENOUGH_COINS', `You need ${w.costCoins} coins`);
      await tx.userWeapon.create({ data: { userId, code } });
    });
    return this.shop(userId);
  }
}
