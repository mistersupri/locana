import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService, Tx } from '../prisma/prisma.service';
import { RealtimeBus } from '../realtime/realtime-bus';

/**
 * In-app notifications. Each kind maps to a privacy preference; SECURITY and SHARING always
 * go through because they are about the user's own account and location.
 * Payloads carry ids and names only, never coordinates.
 */
export type NotificationKind =
  | 'SECURITY'
  | 'SHARING'
  | 'INVITATION'
  | 'ARRIVAL'
  | 'QUEST'
  | 'LEVEL_UP'
  | 'ACHIEVEMENT'
  | 'CIRCLE'
  | 'CHALLENGE';

const PREF: Partial<Record<NotificationKind, 'notifyArrivals' | 'notifyQuests' | 'notifyAchievements' | 'notifyCircle'>> = {
  ARRIVAL: 'notifyArrivals',
  QUEST: 'notifyQuests',
  LEVEL_UP: 'notifyAchievements',
  ACHIEVEMENT: 'notifyAchievements',
  CIRCLE: 'notifyCircle',
  CHALLENGE: 'notifyCircle',
  INVITATION: 'notifyCircle',
};

@Injectable()
export class NotificationsService {
  constructor(private readonly prisma: PrismaService, private readonly bus: RealtimeBus) {}

  async notify(userId: string, kind: NotificationKind, title: string, body: string, data: Record<string, unknown> = {}, tx: Tx = this.prisma) {
    const pref = PREF[kind];
    if (pref) {
      const privacy = await tx.userPrivacySettings.findUnique({ where: { userId } });
      if (privacy && privacy[pref] === false) return null;
    }
    const n = await tx.notification.create({ data: { userId, kind, title, body, data: data as Prisma.InputJsonValue } });
    this.bus.publish({ recipients: [userId], type: 'notification', data: this.view(n) });
    return n;
  }

  view(n: { id: string; kind: string; title: string; body: string; data: unknown; readAt: Date | null; createdAt: Date }) {
    return { id: n.id, kind: n.kind, title: n.title, body: n.body, data: n.data, read: n.readAt !== null, createdAt: n.createdAt };
  }

  async list(userId: string, cursor?: string, limit = 30) {
    const rows = await this.prisma.notification.findMany({
      where: { userId, ...(cursor ? { createdAt: { lt: new Date(cursor) } } : {}) },
      orderBy: { createdAt: 'desc' },
      take: Math.min(limit, 100) + 1,
    });
    const more = rows.length > limit;
    const page = rows.slice(0, limit);
    const unread = await this.prisma.notification.count({ where: { userId, readAt: null } });
    return { items: page.map((n) => this.view(n)), nextCursor: more ? page[page.length - 1].createdAt.toISOString() : null, unread };
  }

  async markRead(userId: string, ids: string[] | 'all') {
    await this.prisma.notification.updateMany({
      where: { userId, readAt: null, ...(ids === 'all' ? {} : { id: { in: ids } }) },
      data: { readAt: new Date() },
    });
  }
}
