import { Injectable } from '@nestjs/common';
import { XpSource } from '@prisma/client';
import { dayKey } from '../common/periods';
import { Tx } from '../prisma/prisma.service';
import { GameEffects } from './effects';
import { LevelService } from './level.service';

/**
 * The only code path that changes XP. The ledger row is inserted with ON CONFLICT DO NOTHING
 * on (user, source, sourceRef); the profile is only incremented when the row was new. So
 * retries, double taps and concurrent requests for the same reward add XP exactly once.
 */
@Injectable()
export class XpService {
  constructor(private readonly levels: LevelService) {}

  async grant(tx: Tx, userId: string, amount: number, source: XpSource, sourceRef: string, fx: GameEffects): Promise<boolean> {
    if (amount <= 0) return false;
    const inserted = await tx.$queryRaw<{ id: string }[]>`
      INSERT INTO "XPTransaction" ("id", "userId", "amount", "source", "sourceRef")
      VALUES (gen_random_uuid(), ${userId}::uuid, ${amount}, ${source}::"XpSource", ${sourceRef})
      ON CONFLICT ("userId", "source", "sourceRef") DO NOTHING
      RETURNING "id"`;
    if (inserted.length === 0) return false;

    const profile = await tx.userProfile.update({
      where: { userId },
      data: { xp: { increment: amount }, lifetimeXp: { increment: amount } },
    });
    const day = new Date(`${dayKey()}T00:00:00Z`);
    await tx.dailyActivityStat.upsert({
      where: { userId_day: { userId, day } },
      create: { userId, day, xp: amount },
      update: { xp: { increment: amount } },
    });
    fx.xpGained += amount;

    const info = await this.levels.info(profile.xp);
    if (info.level > profile.level) {
      // Conditional so two concurrent grants cannot both announce the same level up.
      const moved = await tx.userProfile.updateMany({ where: { userId, level: { lt: info.level } }, data: { level: info.level } });
      if (moved.count === 0) return true;
      fx.levelUps.push({ from: profile.level, to: info.level });
      const curve = await this.levels.curve();
      for (const t of curve) {
        if (t.level > profile.level && t.level <= info.level && t.rewardCollectibleCode) {
          await this.grantCollectible(tx, userId, t.rewardCollectibleCode, `level:${t.level}`, fx);
        }
      }
    }
    return true;
  }

  async grantCollectible(tx: Tx, userId: string, code: string, source: string, fx: GameEffects) {
    const def = await tx.collectibleDefinition.findUnique({ where: { code } });
    if (!def) return false;
    const rows = await tx.$queryRaw<{ userId: string }[]>`
      INSERT INTO "UserCollectible" ("userId", "collectibleId", "source")
      VALUES (${userId}::uuid, ${def.id}::uuid, ${source})
      ON CONFLICT DO NOTHING
      RETURNING "userId"`;
    if (rows.length) fx.collectibles.push({ code: def.code, name: def.name });
    return rows.length > 0;
  }
}
