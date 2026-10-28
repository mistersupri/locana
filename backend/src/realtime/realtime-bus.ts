import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { RedisService } from '../common/redis.service';

/**
 * Event types pushed to clients over the WebSocket. Payloads never contain coordinates
 * except `location.update`, which is only addressed to viewers authorized at send time.
 */
export type RealtimeEventType =
  | 'location.update'
  | 'member.availability'
  | 'sharing.status'
  | 'circle.membership'
  | 'geofence.event'
  | 'quest.progress'
  | 'achievement.unlocked'
  | 'level.up'
  | 'territory.update'
  | 'notification';

export interface RealtimeEnvelope {
  /** user ids that may receive this event; resolved by the sender against current permissions */
  recipients: string[];
  /** when set, only sockets subscribed to this circle receive it */
  circleId?: string;
  type: RealtimeEventType;
  data: unknown;
}

type Deliver = (e: RealtimeEnvelope) => void;
const CHANNEL = 'locana:realtime';

/** Fan-out across instances. Each instance delivers only to sockets it holds. */
@Injectable()
export class RealtimeBus implements OnModuleInit {
  private readonly log = new Logger('RealtimeBus');
  private deliver: Deliver = () => undefined;

  constructor(private readonly redis: RedisService) {}

  onModuleInit() {
    const sub = this.redis.subscriber;
    if (!sub) return;
    void sub.subscribe(CHANNEL).catch((e) => this.log.error(`subscribe failed: ${e.message}`));
    sub.on('message', (channel, raw) => {
      if (channel !== CHANNEL) return;
      try {
        this.deliver(JSON.parse(raw));
      } catch (e) {
        this.log.warn(`dropped malformed realtime message: ${(e as Error).message}`);
      }
    });
  }

  setDeliver(fn: Deliver) {
    this.deliver = fn;
  }

  publish(e: RealtimeEnvelope) {
    if (e.recipients.length === 0) return;
    if (this.redis.client) {
      void this.redis.client.publish(CHANNEL, JSON.stringify(e)).catch((err) => this.log.error(`publish failed: ${err.message}`));
    } else {
      this.deliver(e);
    }
  }
}
