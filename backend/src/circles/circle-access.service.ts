import { Injectable } from '@nestjs/common';
import { CircleMembership, CircleRole } from '@prisma/client';
import { forbidden, notFound } from '../common/errors';
import { PrismaService, Tx } from '../prisma/prisma.service';

/**
 * Single place that answers "may A see B's location?" and "is A a member of circle C?".
 * Every route and every realtime event goes through here, so the rules cannot drift.
 *
 * A viewer sees an owner's coordinates in a circle only when ALL hold:
 *   1. both are members of that circle,
 *   2. the owner has an ACTIVE, unexpired sharing session for that circle,
 *   3. the owner has not paused sharing globally,
 *   4. the owner has not hidden themselves from that viewer in that circle,
 *   5. neither has blocked the other.
 */
@Injectable()
export class CircleAccessService {
  constructor(private readonly prisma: PrismaService) {}

  /** Non-members get 404, not 403, so circle ids cannot be probed. */
  async requireMember(circleId: string, userId: string, tx: Tx = this.prisma): Promise<CircleMembership> {
    const m = await tx.circleMembership.findUnique({ where: { circleId_userId: { circleId, userId } } });
    if (!m) throw notFound('Circle');
    return m;
  }

  async requireRole(circleId: string, userId: string, roles: CircleRole[], tx: Tx = this.prisma) {
    const m = await this.requireMember(circleId, userId, tx);
    if (!roles.includes(m.role)) throw forbidden('ROLE_REQUIRED', `This needs the ${roles.map((r) => r.toLowerCase()).join(' or ')} role`);
    return m;
  }

  async circleIdsOf(userId: string): Promise<string[]> {
    const rows = await this.prisma.circleMembership.findMany({ where: { userId }, select: { circleId: true } });
    return rows.map((r) => r.circleId);
  }

  private async blockedWith(userId: string): Promise<Set<string>> {
    const rows = await this.prisma.userBlock.findMany({ where: { OR: [{ blockerId: userId }, { blockedId: userId }] } });
    return new Set(rows.map((b) => (b.blockerId === userId ? b.blockedId : b.blockerId)));
  }

  /** Circles in which `ownerId` is currently sharing (rules 2 and 3). */
  async sharingCircleIds(ownerId: string, now = new Date()): Promise<string[]> {
    const privacy = await this.prisma.userPrivacySettings.findUnique({ where: { userId: ownerId } });
    if (!privacy || privacy.sharingPaused) return [];
    const sessions = await this.prisma.locationSharingSession.findMany({
      where: { userId: ownerId, status: 'ACTIVE', OR: [{ expiresAt: null }, { expiresAt: { gt: now } }] },
      select: { circleId: true },
    });
    return [...new Set(sessions.map((s) => s.circleId))];
  }

  /**
   * Everyone allowed to receive `ownerId`'s live location right now, grouped by circle.
   * Used to address realtime location events.
   */
  async locationViewers(ownerId: string, now = new Date()): Promise<Map<string, string[]>> {
    const circles = await this.sharingCircleIds(ownerId, now);
    const out = new Map<string, string[]>();
    if (circles.length === 0) return out;
    const [members, hidden, blocked] = await Promise.all([
      this.prisma.circleMembership.findMany({ where: { circleId: { in: circles }, userId: { not: ownerId } }, select: { circleId: true, userId: true } }),
      this.prisma.memberVisibility.findMany({ where: { ownerId, circleId: { in: circles } } }),
      this.blockedWith(ownerId),
    ]);
    const hiddenKey = new Set(hidden.map((h) => `${h.circleId}:${h.viewerId}`));
    for (const m of members) {
      if (blocked.has(m.userId) || hiddenKey.has(`${m.circleId}:${m.userId}`)) continue;
      out.set(m.circleId, [...(out.get(m.circleId) ?? []), m.userId]);
    }
    return out;
  }

  /** Visibility of each member of `circleId` to `viewerId`. Caller must be a member. */
  async visibleOwnersInCircle(circleId: string, viewerId: string, now = new Date()): Promise<Set<string>> {
    const members = await this.prisma.circleMembership.findMany({ where: { circleId }, select: { userId: true } });
    const ids = members.map((m) => m.userId).filter((id) => id !== viewerId);
    if (ids.length === 0) return new Set();
    const [sessions, paused, hidden, blocked] = await Promise.all([
      this.prisma.locationSharingSession.findMany({
        where: { circleId, userId: { in: ids }, status: 'ACTIVE', OR: [{ expiresAt: null }, { expiresAt: { gt: now } }] },
        select: { userId: true },
      }),
      this.prisma.userPrivacySettings.findMany({ where: { userId: { in: ids }, sharingPaused: true }, select: { userId: true } }),
      this.prisma.memberVisibility.findMany({ where: { circleId, viewerId }, select: { ownerId: true } }),
      this.blockedWith(viewerId),
    ]);
    const pausedSet = new Set(paused.map((p) => p.userId));
    const hiddenSet = new Set(hidden.map((h) => h.ownerId));
    return new Set(sessions.map((s) => s.userId).filter((id) => !pausedSet.has(id) && !hiddenSet.has(id) && !blocked.has(id)));
  }

  async canSeeLocation(viewerId: string, ownerId: string, now = new Date()): Promise<boolean> {
    if (viewerId === ownerId) return true;
    const viewers = await this.locationViewers(ownerId, now);
    for (const ids of viewers.values()) if (ids.includes(viewerId)) return true;
    return false;
  }

  /** Users who share at least one circle with `userId` (friends for leaderboards), blocked excluded. */
  async circleMates(userId: string): Promise<string[]> {
    const circles = await this.circleIdsOf(userId);
    if (circles.length === 0) return [];
    const rows = await this.prisma.circleMembership.findMany({ where: { circleId: { in: circles } }, select: { userId: true }, distinct: ['userId'] });
    const blocked = await this.blockedWith(userId);
    return rows.map((r) => r.userId).filter((id) => id !== userId && !blocked.has(id));
  }
}
