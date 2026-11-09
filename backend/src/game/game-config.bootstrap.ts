import { Injectable, Logger, OnApplicationBootstrap } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { seedGameConfig } from './game-config';

/**
 * Applies the game configuration whenever the API starts, so landmarks, achievements, collectibles and
 * quests are there from the first run without a manual seed step. Idempotent; a failure is logged and
 * does not stop the API.
 */
@Injectable()
export class GameConfigBootstrap implements OnApplicationBootstrap {
  private readonly log = new Logger('GameConfig');
  constructor(private readonly prisma: PrismaService) {}

  async onApplicationBootstrap() {
    try {
      await seedGameConfig(this.prisma);
      this.log.log('game configuration is up to date');
    } catch (e) {
      this.log.error(`could not apply the game configuration: ${(e as Error).message}`);
    }
  }
}
