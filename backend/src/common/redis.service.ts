import { Inject, Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import Redis from 'ioredis';
import { APP_ENV, AppEnv } from '../config/env';

/**
 * Optional Redis connections. When REDIS_URL is empty the app runs as a single instance and
 * callers fall back to in-process implementations (rate limiting, realtime fan-out).
 */
@Injectable()
export class RedisService implements OnModuleDestroy {
  private readonly log = new Logger('Redis');
  readonly client: Redis | null;
  readonly subscriber: Redis | null;

  constructor(@Inject(APP_ENV) env: AppEnv) {
    if (!env.redisUrl) {
      this.client = null;
      this.subscriber = null;
      this.log.warn('REDIS_URL not set: using in-process rate limiting and event fan-out (single instance only)');
      return;
    }
    const opts = { maxRetriesPerRequest: 2, lazyConnect: false };
    this.client = new Redis(env.redisUrl, opts);
    this.subscriber = new Redis(env.redisUrl, opts);
    for (const c of [this.client, this.subscriber]) c.on('error', (e) => this.log.error(`Redis error: ${e.message}`));
  }

  async ping(): Promise<boolean> {
    if (!this.client) return true;
    try {
      return (await this.client.ping()) === 'PONG';
    } catch {
      return false;
    }
  }

  async onModuleDestroy() {
    await Promise.all([this.client?.quit(), this.subscriber?.quit()].filter(Boolean));
  }
}
