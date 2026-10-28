import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { LevelInfo, levelFor, Threshold } from './levels';

/** Reads the configurable XP curve from the LevelThreshold table, cached for a minute. */
@Injectable()
export class LevelService {
  private cache: { at: number; rows: (Threshold & { rewardCollectibleCode: string | null })[] } | null = null;

  constructor(private readonly prisma: PrismaService) {}

  async curve() {
    if (this.cache && Date.now() - this.cache.at < 60_000) return this.cache.rows;
    let rows = await this.prisma.levelThreshold.findMany({ orderBy: { level: 'asc' } });
    if (rows.length === 0) rows = [{ level: 1, totalXp: 0, rewardCollectibleCode: null }];
    this.cache = { at: Date.now(), rows };
    return rows;
  }

  invalidate() {
    this.cache = null;
  }

  async info(totalXp: number): Promise<LevelInfo> {
    return levelFor(totalXp, await this.curve());
  }
}
