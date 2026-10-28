import { Global, Module } from '@nestjs/common';
import { NotificationsService } from '../notifications/notifications.service';
import { RealtimeBus } from '../realtime/realtime-bus';
import { RateLimiter } from './rate-limit';
import { RedisService } from './redis.service';

/** Infrastructure shared by every feature module. */
@Global()
@Module({
  providers: [RedisService, RateLimiter, RealtimeBus, NotificationsService],
  exports: [RedisService, RateLimiter, RealtimeBus, NotificationsService],
})
export class CommonModule {}
