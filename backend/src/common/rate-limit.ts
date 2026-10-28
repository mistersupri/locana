import { CanActivate, ExecutionContext, HttpStatus, Injectable, SetMetadata } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ApiError } from './errors';
import { RedisService } from './redis.service';

export interface RateLimitRule {
  /** bucket name, shared across routes that use the same name */
  name: string;
  limit: number;
  windowSec: number;
  /** 'user' falls back to 'ip' on public routes */
  by?: 'user' | 'ip';
}

export const RATE_LIMIT = 'rateLimit';
export const RateLimit = (rule: RateLimitRule) => SetMetadata(RATE_LIMIT, rule);

/** Fixed-window counter. Redis when configured (shared by all instances), memory otherwise. */
@Injectable()
export class RateLimiter {
  private readonly memory = new Map<string, { count: number; resetAt: number }>();

  constructor(private readonly redis: RedisService) {}

  /** Returns remaining requests, throws 429 when exhausted. */
  async hit(key: string, limit: number, windowSec: number, now = Date.now()): Promise<number> {
    let count: number;
    if (this.redis.client) {
      const k = `rl:${key}`;
      const res = await this.redis.client.multi().incr(k).expire(k, windowSec, 'NX').exec();
      count = Number(res?.[0]?.[1] ?? 0);
    } else {
      const entry = this.memory.get(key);
      if (!entry || entry.resetAt <= now) {
        this.memory.set(key, { count: 1, resetAt: now + windowSec * 1000 });
        count = 1;
        if (this.memory.size > 50_000) this.sweep(now);
      } else {
        count = ++entry.count;
      }
    }
    if (count > limit) {
      throw new ApiError(HttpStatus.TOO_MANY_REQUESTS, 'RATE_LIMITED', 'Too many requests, slow down and try again shortly');
    }
    return limit - count;
  }

  private sweep(now: number) {
    for (const [k, v] of this.memory) if (v.resetAt <= now) this.memory.delete(k);
  }
}

@Injectable()
export class RateLimitGuard implements CanActivate {
  constructor(private readonly reflector: Reflector, private readonly limiter: RateLimiter) {}

  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const rule = this.reflector.getAllAndOverride<RateLimitRule | undefined>(RATE_LIMIT, [ctx.getHandler(), ctx.getClass()]);
    const req = ctx.switchToHttp().getRequest();
    // Every authenticated route gets a generous default ceiling per user.
    const effective: RateLimitRule = rule ?? { name: 'default', limit: 600, windowSec: 60, by: 'user' };
    const subject = effective.by !== 'ip' && req.user?.id ? `u:${req.user.id}` : `ip:${req.ip}`;
    await this.limiter.hit(`${effective.name}:${subject}`, effective.limit, effective.windowSec);
    return true;
  }
}
