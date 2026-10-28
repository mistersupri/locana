import { Injectable } from '@nestjs/common';
import { CircleRole } from '@prisma/client';
import { humanCode, normalizeCode, sha256 } from '../common/crypto';
import { badRequest, forbidden, notFound } from '../common/errors';
import { NotificationsService } from '../notifications/notifications.service';
import { PrismaService, Tx } from '../prisma/prisma.service';
import { RealtimeBus } from '../realtime/realtime-bus';
import { CircleAccessService } from './circle-access.service';
import { CreateCircleDto, CreateInvitationDto, UpdateCircleDto } from './circles.dto';
import { FeedService } from './feed.service';

export const MAX_CIRCLE_MEMBERS = 50;
export const STALE_AFTER_MS = 10 * 60_000;

const formatCode = (c: string) => `${c.slice(0, 4)}-${c.slice(4, 8)}-${c.slice(8)}`;

@Injectable()
export class CirclesService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly access: CircleAccessService,
    private readonly feed: FeedService,
    private readonly notifications: NotificationsService,
    private readonly bus: RealtimeBus,
  ) {}

  async create(userId: string, dto: CreateCircleDto) {
    const circle = await this.prisma.$transaction(async (tx) => {
      const c = await tx.circle.create({ data: { name: dto.name, avatarKey: dto.avatarKey } });
      await tx.circleMembership.create({ data: { circleId: c.id, userId, role: 'OWNER' } });
      await this.feed.post(c.id, userId, 'JOINED', { role: 'OWNER' }, tx);
      return c;
    });
    return this.get(circle.id, userId);
  }

  async list(userId: string) {
    const rows = await this.prisma.circleMembership.findMany({
      where: { userId },
      include: { circle: { include: { _count: { select: { memberships: true } } } } },
      orderBy: { joinedAt: 'asc' },
    });
    const sharing = new Set(await this.access.sharingCircleIds(userId));
    return rows.map((m) => ({
      id: m.circle.id,
      name: m.circle.name,
      avatarKey: m.circle.avatarKey,
      memberCount: m.circle._count.memberships,
      myRole: m.role,
      muted: m.muted,
      iAmSharing: sharing.has(m.circle.id),
    }));
  }

  /** Circle detail with members. Location status is included; coordinates are not (see /locations). */
  async get(circleId: string, viewerId: string) {
    const me = await this.access.requireMember(circleId, viewerId);
    const circle = await this.prisma.circle.findUniqueOrThrow({
      where: { id: circleId },
      include: {
        memberships: {
          orderBy: { joinedAt: 'asc' },
          include: { user: { select: { id: true, username: true, profile: { select: { displayName: true, avatarKey: true, avatarFrameKey: true, level: true, titleKey: true } } } } },
        },
      },
    });
    const visible = await this.access.visibleOwnersInCircle(circleId, viewerId);
    const hiddenFrom = await this.prisma.memberVisibility.findMany({ where: { circleId, ownerId: viewerId }, select: { viewerId: true } });
    const blockedByMe = new Set((await this.prisma.userBlock.findMany({ where: { blockerId: viewerId } })).map((b) => b.blockedId));
    return {
      id: circle.id,
      name: circle.name,
      avatarKey: circle.avatarKey,
      createdAt: circle.createdAt,
      myRole: me.role,
      muted: me.muted,
      hiddenFromViewerIds: hiddenFrom.map((h) => h.viewerId),
      members: circle.memberships.map((m) => ({
        userId: m.userId,
        username: m.user.username,
        displayName: m.user.profile?.displayName ?? m.user.username,
        avatarKey: m.user.profile?.avatarKey,
        avatarFrameKey: m.user.profile?.avatarFrameKey,
        titleKey: m.user.profile?.titleKey,
        level: m.user.profile?.level ?? 1,
        role: m.role,
        joinedAt: m.joinedAt,
        sharingWithMe: m.userId === viewerId ? null : visible.has(m.userId),
        blockedByMe: blockedByMe.has(m.userId),
      })),
    };
  }

  async update(circleId: string, userId: string, dto: UpdateCircleDto) {
    await this.access.requireRole(circleId, userId, ['OWNER', 'ADMIN']);
    await this.prisma.circle.update({ where: { id: circleId }, data: { name: dto.name, avatarKey: dto.avatarKey } });
    return this.get(circleId, userId);
  }

  async remove(circleId: string, userId: string) {
    await this.access.requireRole(circleId, userId, ['OWNER']);
    const members = await this.prisma.circleMembership.findMany({ where: { circleId }, select: { userId: true } });
    await this.prisma.$transaction([
      this.prisma.userProfile.updateMany({ where: { teamCircleId: circleId }, data: { teamCircleId: null } }),
      this.prisma.circle.delete({ where: { id: circleId } }),
      this.prisma.auditEvent.create({ data: { actorId: userId, action: 'circle.delete', targetType: 'circle', targetId: circleId } }),
    ]);
    this.bus.publish({ recipients: members.map((m) => m.userId), type: 'circle.membership', data: { circleId, change: 'DELETED' } });
  }

  // ───────────── invitations ─────────────

  async createInvitation(circleId: string, userId: string, dto: CreateInvitationDto) {
    await this.access.requireRole(circleId, userId, ['OWNER', 'ADMIN']);
    const active = await this.prisma.circleInvitation.count({ where: { circleId, revokedAt: null, expiresAt: { gt: new Date() } } });
    if (active >= 20) throw badRequest('TOO_MANY_INVITATIONS', 'Revoke an existing code before making another');
    const code = humanCode(10);
    const inv = await this.prisma.circleInvitation.create({
      data: {
        circleId,
        createdById: userId,
        codeHash: sha256(code),
        codeHint: code.slice(-2),
        maxUses: dto.maxUses ?? 5,
        expiresAt: new Date(Date.now() + (dto.expiresInHours ?? 48) * 3_600_000),
      },
    });
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'circle.invite_create', targetType: 'invitation', targetId: inv.id } });
    const pretty = formatCode(code);
    // The plain code exists only in this response; the database keeps a hash.
    return { ...this.invitationView(inv), code: pretty, deepLink: `locana://join?code=${pretty}` };
  }

  private invitationView(i: { id: string; codeHint: string; maxUses: number; uses: number; expiresAt: Date; revokedAt: Date | null; createdAt: Date }) {
    const now = new Date();
    return {
      id: i.id,
      codeHint: i.codeHint,
      maxUses: i.maxUses,
      uses: i.uses,
      expiresAt: i.expiresAt,
      createdAt: i.createdAt,
      status: i.revokedAt ? 'REVOKED' : i.expiresAt <= now ? 'EXPIRED' : i.uses >= i.maxUses ? 'USED_UP' : 'ACTIVE',
    };
  }

  async listInvitations(circleId: string, userId: string) {
    await this.access.requireRole(circleId, userId, ['OWNER', 'ADMIN']);
    const rows = await this.prisma.circleInvitation.findMany({ where: { circleId }, orderBy: { createdAt: 'desc' }, take: 50 });
    return rows.map((r) => this.invitationView(r));
  }

  async revokeInvitation(circleId: string, invitationId: string, userId: string) {
    await this.access.requireRole(circleId, userId, ['OWNER', 'ADMIN']);
    const r = await this.prisma.circleInvitation.updateMany({ where: { id: invitationId, circleId, revokedAt: null }, data: { revokedAt: new Date() } });
    if (r.count === 0) throw notFound('Invitation');
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'circle.invite_revoke', targetType: 'invitation', targetId: invitationId } });
  }

  async join(userId: string, rawCode: string) {
    const code = normalizeCode(rawCode);
    const invalid = badRequest('INVALID_INVITATION', 'This invitation code is not valid or has expired');
    if (code.length !== 10) throw invalid;
    const inv = await this.prisma.circleInvitation.findUnique({ where: { codeHash: sha256(code) } });
    if (!inv || inv.revokedAt || inv.expiresAt <= new Date()) throw invalid;
    const existing = await this.prisma.circleMembership.findUnique({ where: { circleId_userId: { circleId: inv.circleId, userId } } });
    if (existing) return this.get(inv.circleId, userId);

    const blockedWithMember = await this.prisma.userBlock.count({
      where: {
        OR: [
          { blockerId: userId, blocked: { memberships: { some: { circleId: inv.circleId } } } },
          { blockedId: userId, blocker: { memberships: { some: { circleId: inv.circleId } } } },
        ],
      },
    });
    if (blockedWithMember > 0) throw invalid;

    await this.prisma.$transaction(async (tx) => {
      // Conditional increment: two people racing for the last seat cannot both get in.
      const claimed = await tx.circleInvitation.updateMany({
        where: { id: inv.id, revokedAt: null, uses: { lt: inv.maxUses } },
        data: { uses: { increment: 1 } },
      });
      if (claimed.count !== 1) throw invalid;
      const count = await tx.circleMembership.count({ where: { circleId: inv.circleId } });
      if (count >= MAX_CIRCLE_MEMBERS) throw badRequest('CIRCLE_FULL', `Circles hold up to ${MAX_CIRCLE_MEMBERS} people`);
      await tx.circleMembership.create({ data: { circleId: inv.circleId, userId, role: 'MEMBER' } });
      await this.feed.post(inv.circleId, userId, 'JOINED', {}, tx);
      await tx.auditEvent.create({ data: { actorId: userId, action: 'circle.join', targetType: 'circle', targetId: inv.circleId } });
    });
    await this.announceMembership(inv.circleId, userId, 'JOINED');
    return this.get(inv.circleId, userId);
  }

  private async announceMembership(circleId: string, userId: string, change: 'JOINED' | 'LEFT' | 'REMOVED' | 'ROLE_CHANGED', extraRecipients: string[] = []) {
    const members = await this.prisma.circleMembership.findMany({ where: { circleId }, select: { userId: true, muted: true } });
    this.bus.publish({ recipients: [...new Set([...members.map((m) => m.userId), ...extraRecipients])], type: 'circle.membership', data: { circleId, userId, change } });
    if (change === 'JOINED') {
      const [circle, profile] = await Promise.all([
        this.prisma.circle.findUnique({ where: { id: circleId } }),
        this.prisma.userProfile.findUnique({ where: { userId } }),
      ]);
      for (const m of members) {
        if (m.userId === userId || m.muted) continue;
        await this.notifications.notify(m.userId, 'CIRCLE', `${profile?.displayName ?? 'Someone'} joined ${circle?.name}`, 'Say hi in the circle feed.', { circleId });
      }
    }
  }

  // ───────────── members ─────────────

  async setRole(circleId: string, actorId: string, targetId: string, role: CircleRole) {
    await this.access.requireRole(circleId, actorId, ['OWNER']);
    if (actorId === targetId) throw badRequest('CANNOT_CHANGE_OWN_ROLE', 'Transfer ownership to someone else instead');
    await this.access.requireMember(circleId, targetId);
    await this.prisma.$transaction(async (tx) => {
      if (role === 'OWNER') {
        await tx.circleMembership.update({ where: { circleId_userId: { circleId, userId: actorId } }, data: { role: 'ADMIN' } });
      }
      await tx.circleMembership.update({ where: { circleId_userId: { circleId, userId: targetId } }, data: { role } });
      await tx.auditEvent.create({ data: { actorId, action: 'circle.set_role', targetType: 'user', targetId, meta: { circleId, role } } });
    });
    await this.announceMembership(circleId, targetId, 'ROLE_CHANGED');
    return this.get(circleId, actorId);
  }

  async removeMember(circleId: string, actorId: string, targetId: string) {
    if (actorId === targetId) return this.leave(circleId, actorId);
    const actor = await this.access.requireRole(circleId, actorId, ['OWNER', 'ADMIN']);
    const target = await this.access.requireMember(circleId, targetId);
    if (target.role === 'OWNER' || (target.role === 'ADMIN' && actor.role !== 'OWNER')) {
      throw forbidden('ROLE_REQUIRED', 'Only the owner can remove an admin, and nobody can remove the owner');
    }
    await this.prisma.$transaction(async (tx) => {
      await this.detach(tx, circleId, targetId);
      await tx.auditEvent.create({ data: { actorId, action: 'circle.remove_member', targetType: 'user', targetId, meta: { circleId } } });
    });
    await this.announceMembership(circleId, targetId, 'REMOVED', [targetId]);
  }

  /** Leaving as the owner hands the circle to the longest-standing admin, then member. Last one out deletes it. */
  async leave(circleId: string, userId: string, tx?: Tx) {
    const run = async (t: Tx) => {
      const me = await this.access.requireMember(circleId, userId, t);
      if (me.role === 'OWNER') {
        const heir =
          (await t.circleMembership.findFirst({ where: { circleId, role: 'ADMIN' }, orderBy: { joinedAt: 'asc' } })) ??
          (await t.circleMembership.findFirst({ where: { circleId, userId: { not: userId } }, orderBy: { joinedAt: 'asc' } }));
        if (!heir) {
          await t.userProfile.updateMany({ where: { teamCircleId: circleId }, data: { teamCircleId: null } });
          await t.circle.delete({ where: { id: circleId } });
          return 'DELETED' as const;
        }
        await t.circleMembership.update({ where: { id: heir.id }, data: { role: 'OWNER' } });
      }
      await this.detach(t, circleId, userId);
      await this.feed.post(circleId, userId, 'LEFT', {}, t);
      return 'LEFT' as const;
    };
    const result = tx ? await run(tx) : await this.prisma.$transaction(run);
    if (result === 'LEFT') await this.announceMembership(circleId, userId, 'LEFT', [userId]);
  }

  /** Removes a member and everything that let them see or be seen in this circle. */
  private async detach(tx: Tx, circleId: string, userId: string) {
    await tx.locationSharingSession.updateMany({
      where: { circleId, userId, status: { in: ['ACTIVE', 'PAUSED'] } },
      data: { status: 'ENDED', endedAt: new Date(), endReason: 'LEFT_CIRCLE' },
    });
    await tx.memberVisibility.deleteMany({ where: { circleId, OR: [{ ownerId: userId }, { viewerId: userId }] } });
    await tx.userProfile.updateMany({ where: { userId, teamCircleId: circleId }, data: { teamCircleId: null } });
    await tx.circleMembership.delete({ where: { circleId_userId: { circleId, userId } } });
  }

  async setMuted(circleId: string, userId: string, muted: boolean) {
    await this.access.requireMember(circleId, userId);
    await this.prisma.circleMembership.update({ where: { circleId_userId: { circleId, userId } }, data: { muted } });
  }

  /** Hide or show the caller's location from one specific member of this circle. */
  async setHiddenFrom(circleId: string, ownerId: string, viewerId: string, hidden: boolean) {
    await this.access.requireMember(circleId, ownerId);
    await this.access.requireMember(circleId, viewerId);
    if (ownerId === viewerId) throw badRequest('INVALID_VIEWER', 'Pick another member');
    if (hidden) {
      await this.prisma.memberVisibility.upsert({
        where: { circleId_ownerId_viewerId: { circleId, ownerId, viewerId } },
        create: { circleId, ownerId, viewerId },
        update: {},
      });
      this.bus.publish({ recipients: [viewerId], circleId, type: 'member.availability', data: { circleId, userId: ownerId, status: 'HIDDEN' } });
    } else {
      await this.prisma.memberVisibility.deleteMany({ where: { circleId, ownerId, viewerId } });
    }
  }
}
