import { RateLimiter } from './rate-limit';
import { RedisService } from './redis.service';

describe('RateLimiter (in-process)', () => {
  const limiter = new RateLimiter({ client: null } as unknown as RedisService);

  it('allows up to the limit then rejects with RATE_LIMITED', async () => {
    const now = 1_000_000;
    await limiter.hit('k', 2, 60, now);
    await limiter.hit('k', 2, 60, now);
    await expect(limiter.hit('k', 2, 60, now)).rejects.toMatchObject({ code: 'RATE_LIMITED' });
  });

  it('opens a new window after it expires', async () => {
    const now = 2_000_000;
    await limiter.hit('w', 1, 1, now);
    await expect(limiter.hit('w', 1, 1, now)).rejects.toBeDefined();
    await expect(limiter.hit('w', 1, 1, now + 1_001)).resolves.toBe(0);
  });
});
