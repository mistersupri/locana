import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { LocationSharingSession } from '@prisma/client';
import { badRequest, notFound } from '../common/errors';
import { CircleAccessService } from '../circles/circle-access.service';
import { NotificationsService } from '../notifications/notifications.service';
import { PrismaService } from '../prisma/prisma.service';
import { RealtimeBus } from '../realtime/realtime-bus';

/**
 * Location sharing is opt-in per circle through explicit sessions. Nothing here starts
 * a session implicitly: not login, not reinstall, not a permission grant.
 */
@Injectable()
export class SharingService {
  private readonly log = new Logger('Sharing');

  constructor(
    private readonly prisma: PrismaService,
    private readonly access: CircleAccessService,
    private readonly bus: RealtimeBus,
    private readonly notifications: NotificationsService,
  ) {}

  view(s: LocationSharingSession, circleName?: string) {
    return {
      id: s.id,
      circleId: s.circleId,
      circleName,
      status: s.status,
      startedAt: s.startedAt,
      expiresAt: s.expiresAt,
      pausedAt: s.pausedAt,
      endedAt: s.endedAt,
      endReason: s.endReason,
    };
  }

  async state(userId: string) {
    const [privacy, sessions] = await Promise.all([
      this.prisma.userPrivacySettings.findUniqueOrThrow({ where: { userId } }),
      this.prisma.locationSharingSession.findMany({
        where: { userId, status: { in: ['ACTIVE', 'PAUSED'] } },
        include: { circle: { select: { name: true } } },
        orderBy: { startedAt: 'asc' },
      }),
    ]);
    const now = new Date();
    const live = sessions.filter((s) => !s.expiresAt || s.expiresAt > now);
    return {
      globallyPaused: privacy.sharingPaused,
      explorationEnabled: privacy.explorationEnabled,
      // The device should collect location only when this is true.
      collectLocation: (!privacy.sharingPaused && live.some((s) => s.status === 'ACTIVE')) || privacy.explorationEnabled,
      sessions: live.map((s) => this.view(s, s.circle.name)),
    };
  }

  async start(userId: string, circleId: string, durationMinutes?: number) {
    await this.access.requireMember(circleId, userId);
    const now = new Date();
    await this.prisma.$transaction(async (tx) => {
      await tx.locationSharingSession.updateMany({
        where: { userId, circleId, status: { in: ['ACTIVE', 'PAUSED'] } },
        data: { status: 'ENDED', endedAt: now, endReason: 'REPLACED' },
      });
      await tx.locationSharingSession.create({
        data: { userId, circleId, status: 'ACTIVE', expiresAt: durationMinutes ? new Date(now.getTime() + durationMinutes * 60_000) : null },
      });
      await tx.auditEvent.create({ data: { actorId: userId, action: 'sharing.start', targetType: 'circle', targetId: circleId, meta: { durationMinutes: durationMinutes ?? null } } });
    });
    await this.announce(userId, [circleId]);
    return this.state(userId);
  }

  private async own(userId: string, sessionId: string) {
    const s = await this.prisma.locationSharingSession.findFirst({ where: { id: sessionId, userId } });
    if (!s) throw notFound('Sharing session');
    return s;
  }

  async pause(userId: string, sessionId: string) {
    const s = await this.own(userId, sessionId);
    if (s.status !== 'ACTIVE') throw badRequest('NOT_ACTIVE', 'Only active sharing can be paused');
    await this.prisma.locationSharingSession.update({ where: { id: s.id }, data: { status: 'PAUSED', pausedAt: new Date() } });
    await this.announce(userId, [s.circleId]);
    return this.state(userId);
  }

  async resume(userId: string, sessionId: string) {
    const s = await this.own(userId, sessionId);
    if (s.status !== 'PAUSED') throw badRequest('NOT_PAUSED', 'This sharing is not paused');
    if (s.expiresAt && s.expiresAt <= new Date()) throw badRequest('SESSION_EXPIRED', 'This sharing already expired; start a new one');
    await this.prisma.locationSharingSession.update({ where: { id: s.id }, data: { status: 'ACTIVE', pausedAt: null } });
    await this.announce(userId, [s.circleId]);
    return this.state(userId);
  }

  async stop(userId: string, sessionId: string) {
    const s = await this.own(userId, sessionId);
    if (s.status === 'ENDED') return this.state(userId);
    await this.prisma.locationSharingSession.update({ where: { id: s.id }, data: { status: 'ENDED', endedAt: new Date(), endReason: 'STOPPED' } });
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'sharing.stop', targetType: 'circle', targetId: s.circleId } });
    await this.afterSharingChange(userId);
    await this.announce(userId, [s.circleId]);
    return this.state(userId);
  }

  /** One switch that hides the user everywhere at once, without losing their per-circle setup. */
  async setGlobalPause(userId: string, paused: boolean) {
    await this.prisma.userPrivacySettings.update({ where: { userId }, data: { sharingPaused: paused } });
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: paused ? 'sharing.pause_all' : 'sharing.resume_all' } });
    await this.announce(userId, await this.access.circleIdsOf(userId));
    return this.state(userId);
  }

  async stopAll(userId: string, reason: string) {
    const ended = await this.prisma.locationSharingSession.findMany({ where: { userId, status: { in: ['ACTIVE', 'PAUSED'] } }, select: { circleId: true } });
    await this.prisma.locationSharingSession.updateMany({
      where: { userId, status: { in: ['ACTIVE', 'PAUSED'] } },
      data: { status: 'ENDED', endedAt: new Date(), endReason: reason },
    });
    await this.afterSharingChange(userId);
    await this.announce(userId, ended.map((e) => e.circleId));
  }

  /** Drop the stored latest position once nothing needs it any more. */
  async afterSharingChange(userId: string) {
    const st = await this.state(userId);
    if (!st.collectLocation) await this.prisma.latestLocation.deleteMany({ where: { userId } });
  }

  /**
   * Tells each circle whether this member is visible to them now. Members who lost access
   * get HIDDEN, so their app clears the marker instead of showing a frozen position.
   */
  async announce(userId: string, circleIds: string[]) {
    if (circleIds.length === 0) return;
    const viewers = await this.access.locationViewers(userId);
    const st = await this.state(userId);
    this.bus.publish({ recipients: [userId], type: 'sharing.status', data: st });
    for (const circleId of circleIds) {
      const members = await this.prisma.circleMembership.findMany({ where: { circleId, userId: { not: userId } }, select: { userId: true } });
      const allowed = new Set(viewers.get(circleId) ?? []);
      const visible = members.filter((m) => allowed.has(m.userId)).map((m) => m.userId);
      const hidden = members.filter((m) => !allowed.has(m.userId)).map((m) => m.userId);
      this.bus.publish({ recipients: visible, circleId, type: 'member.availability', data: { circleId, userId, status: 'SHARING' } });
      this.bus.publish({ recipients: hidden, circleId, type: 'member.availability', data: { circleId, userId, status: 'HIDDEN' } });
    }
  }

  @Cron(CronExpression.EVERY_MINUTE)
  async expireSessions(now = new Date()) {
    const due = await this.prisma.locationSharingSession.findMany({
      where: { status: { in: ['ACTIVE', 'PAUSED'] }, expiresAt: { lte: now } },
      include: { circle: { select: { name: true } } },
      take: 500,
    });
    for (const s of due) {
      const r = await this.prisma.locationSharingSession.updateMany({
        where: { id: s.id, status: { in: ['ACTIVE', 'PAUSED'] } },
        data: { status: 'ENDED', endedAt: now, endReason: 'EXPIRED' },
      });
      if (r.count === 0) continue; // another instance got it
      await this.notifications.notify(s.userId, 'SHARING', 'Sharing ended', `Your location is no longer shared with ${s.circle.name}.`, { circleId: s.circleId });
      await this.afterSharingChange(s.userId);
      await this.announce(s.userId, [s.circleId]);
    }
    if (due.length) this.log.log(`expired ${due.length} sharing session(s)`);
  }
}
