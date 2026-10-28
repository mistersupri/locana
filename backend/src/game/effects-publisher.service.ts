import { Injectable, Logger } from '@nestjs/common';
import { FeedService } from '../circles/feed.service';
import { NotificationsService } from '../notifications/notifications.service';
import { PrismaService } from '../prisma/prisma.service';
import { RealtimeBus } from '../realtime/realtime-bus';
import { GameEffects } from './effects';

/** Turns committed game effects into notifications, feed items and realtime events. */
@Injectable()
export class EffectsPublisher {
  private readonly log = new Logger('GameEffects');

  constructor(
    private readonly prisma: PrismaService,
    private readonly notifications: NotificationsService,
    private readonly feed: FeedService,
    private readonly bus: RealtimeBus,
  ) {}

  async publish(userId: string, fx: GameEffects) {
    try {
      await this.run(userId, fx);
    } catch (e) {
      // The rewards are already committed; a failed notification must not surface as an error.
      this.log.error(`publishing effects failed: ${(e as Error).message}`);
    }
  }

  private async run(userId: string, fx: GameEffects) {
    for (const l of fx.levelUps) {
      this.bus.publish({ recipients: [userId], type: 'level.up', data: { from: l.from, to: l.to, collectibles: fx.collectibles } });
      await this.notifications.notify(userId, 'LEVEL_UP', `Level ${l.to}`, 'You reached a new level. Open Play to see what unlocked.', { level: l.to });
      await this.feed.postToAllCircles(userId, 'LEVEL_UP', { level: l.to });
    }
    for (const a of fx.achievements) {
      this.bus.publish({ recipients: [userId], type: 'achievement.unlocked', data: a });
      await this.notifications.notify(userId, 'ACHIEVEMENT', a.name, `Achievement unlocked${a.rewardXp ? `, +${a.rewardXp} XP` : ''}.`, { code: a.code });
      await this.feed.postToAllCircles(userId, 'ACHIEVEMENT', { code: a.code, name: a.name, rarity: a.rarity });
    }
    for (const q of fx.questProgress.values()) {
      this.bus.publish({ recipients: [q.userId], type: 'quest.progress', data: q });
      if (q.completed) await this.notifications.notify(q.userId, 'QUEST', `${q.questName} complete`, 'Claim your reward in Explore.', { userQuestId: q.userQuestId });
    }
    for (const t of fx.territoryChanges) {
      const circles = [t.ownerCircleId, t.previousOwnerCircleId].filter((c): c is string => !!c);
      const members = await this.prisma.circleMembership.findMany({ where: { circleId: { in: circles } }, select: { userId: true } });
      this.bus.publish({ recipients: [...new Set(members.map((m) => m.userId))], type: 'territory.update', data: t });
      if (t.ownerCircleId) await this.feed.post(t.ownerCircleId, userId, 'TERRITORY', { territoryId: t.territoryId, name: t.name });
    }
    for (const c of fx.challengeCompletions) {
      const members = await this.prisma.circleMembership.findMany({ where: { circleId: c.circleId, muted: false }, select: { userId: true } });
      for (const m of members) await this.notifications.notify(m.userId, 'CHALLENGE', 'Circle challenge complete', c.title, { circleId: c.circleId });
    }
  }
}
