import { HttpStatus, Injectable } from '@nestjs/common';
import { verifyPassword } from '../auth/passwords';
import { TokenService } from '../auth/token.service';
import { CircleAccessService } from '../circles/circle-access.service';
import { CirclesService } from '../circles/circles.service';
import { ApiError, badRequest, conflict, notFound } from '../common/errors';
import { LevelService } from '../game/level.service';
import { SharingService } from '../location/sharing.service';
import { PrismaService } from '../prisma/prisma.service';
import { CosmeticsDto, PrivacyDto, UpdateProfileDto } from './users.dto';

@Injectable()
export class UsersService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly levels: LevelService,
    private readonly access: CircleAccessService,
    private readonly circles: CirclesService,
    private readonly sharing: SharingService,
    private readonly tokens: TokenService,
  ) {}

  /** Largest photo accepted. The app scales to about 384 px first, which is a few tens of kilobytes. */
  static readonly MAX_PHOTO_BYTES = 400 * 1024;

  private static sniff(buf: Buffer): string | null {
    if (buf.length > 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'image/jpeg';
    if (buf.length > 8 && buf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
    if (buf.length > 12 && buf.subarray(0, 4).toString() === 'RIFF' && buf.subarray(8, 12).toString() === 'WEBP') return 'image/webp';
    return null;
  }

  async setPhoto(userId: string, body: unknown) {
    if (!Buffer.isBuffer(body) || body.length === 0) throw badRequest('INVALID_IMAGE', 'Send the image as the request body with an image content type');
    if (body.length > UsersService.MAX_PHOTO_BYTES) throw badRequest('IMAGE_TOO_LARGE', 'The photo must be under 400 KB; the app scales it down first');
    // Trust the bytes, not the header: only real JPEG, PNG or WebP files are stored.
    const contentType = UsersService.sniff(body);
    if (!contentType) throw badRequest('INVALID_IMAGE', 'Only JPEG, PNG or WebP photos are accepted');
    const now = new Date();
    await this.prisma.$transaction([
      this.prisma.avatarPhoto.upsert({ where: { userId }, create: { userId, contentType, data: body, updatedAt: now }, update: { contentType, data: body, updatedAt: now } }),
      this.prisma.userProfile.update({ where: { userId }, data: { avatarPhotoAt: now } }),
    ]);
    return { avatarPhotoVersion: now.getTime() };
  }

  async removePhoto(userId: string) {
    await this.prisma.$transaction([
      this.prisma.avatarPhoto.deleteMany({ where: { userId } }),
      this.prisma.userProfile.update({ where: { userId }, data: { avatarPhotoAt: null } }),
    ]);
    return { avatarPhotoVersion: null };
  }

  /** The photo of yourself, or of someone you share a circle with (and have not blocked or been blocked by). */
  async photoFor(viewerId: string, ownerId: string) {
    if (viewerId !== ownerId) {
      const shared = await this.prisma.circleMembership.findFirst({
        where: { userId: ownerId, circle: { memberships: { some: { userId: viewerId } } } },
        select: { circleId: true },
      });
      const blocked = await this.prisma.userBlock.findFirst({
        where: { OR: [{ blockerId: viewerId, blockedId: ownerId }, { blockerId: ownerId, blockedId: viewerId }] },
        select: { blockerId: true },
      });
      if (!shared || blocked) throw notFound('Photo');
    }
    const photo = await this.prisma.avatarPhoto.findUnique({ where: { userId: ownerId } });
    if (!photo) throw notFound('Photo');
    return photo;
  }

  async me(userId: string) {
    const u = await this.prisma.user.findUnique({ where: { id: userId }, include: { profile: true, privacy: true } });
    if (!u || u.deletedAt || !u.profile || !u.privacy) throw notFound('User');
    const p = u.profile;
    const level = await this.levels.info(p.xp);
    return {
      id: u.id,
      email: u.email,
      username: u.username,
      hasPassword: !!u.passwordHash,
      googleLinked: !!u.googleSub,
      createdAt: u.createdAt,
      profile: {
        displayName: p.displayName,
        avatarKey: p.avatarKey,
        avatarPhotoVersion: p.avatarPhotoAt?.getTime() ?? null,
        avatarFrameKey: p.avatarFrameKey,
        titleKey: p.titleKey,
        markerKey: p.markerKey,
        bio: p.bio,
        onboardingDone: p.onboardingDone,
        teamCircleId: p.teamCircleId,
      },
      progress: {
        level: level.level,
        totalXp: p.xp,
        lifetimeXp: p.lifetimeXp,
        levelStartXp: level.levelStartXp,
        nextLevelXp: level.nextLevelXp,
        levelProgress: level.progress,
        currentStreak: p.currentStreak,
        longestStreak: p.longestStreak,
        tilesDiscovered: p.tilesDiscovered,
        questsCompleted: p.questsCompleted,
        totalDistanceM: Math.round(p.totalDistanceM),
      },
      privacy: this.privacyView(u.privacy),
    };
  }

  private privacyView(p: NonNullable<Awaited<ReturnType<PrismaService['userPrivacySettings']['findUnique']>>>) {
    return {
      sharingPaused: p.sharingPaused,
      historyEnabled: p.historyEnabled,
      historyRetentionDays: p.historyRetentionDays,
      explorationEnabled: p.explorationEnabled,
      showInLeaderboards: p.showInLeaderboards,
      leaderboardAlias: p.leaderboardAlias,
      shareExplorationWithCircles: p.shareExplorationWithCircles,
      notifyArrivals: p.notifyArrivals,
      notifyQuests: p.notifyQuests,
      notifyAchievements: p.notifyAchievements,
      notifyCircle: p.notifyCircle,
      notifyTrips: p.notifyTrips,
    };
  }

  async freeUsername(seed: string): Promise<string> {
    const base = seed.toLowerCase().replace(/[^a-z0-9_.]/g, '').slice(0, 18).padEnd(3, '0');
    for (let i = 0; i < 20; i++) {
      const candidate = i === 0 ? base : `${base}${Math.floor(Math.random() * 10_000)}`;
      if (!(await this.prisma.user.findUnique({ where: { username: candidate } }))) return candidate;
    }
    return `${base}${Date.now() % 1_000_000}`;
  }

  async updateProfile(userId: string, dto: UpdateProfileDto) {
    if (dto.username) {
      const other = await this.prisma.user.findUnique({ where: { username: dto.username } });
      if (other && other.id !== userId) throw conflict('USERNAME_TAKEN', 'That username is taken');
      await this.prisma.user.update({ where: { id: userId }, data: { username: dto.username } });
    }
    if (dto.teamCircleId) await this.access.requireMember(dto.teamCircleId, userId);
    await this.prisma.userProfile.update({
      where: { userId },
      data: {
        displayName: dto.displayName,
        avatarKey: dto.avatarKey,
        bio: dto.bio,
        onboardingDone: dto.onboardingDone,
        teamCircleId: dto.teamCircleId,
      },
    });
    return this.me(userId);
  }

  /** Cosmetics can only be equipped once owned. */
  async setCosmetics(userId: string, dto: CosmeticsDto) {
    const kinds = { avatarFrameKey: 'AVATAR_FRAME', titleKey: 'TITLE', markerKey: 'MAP_MARKER' } as const;
    for (const [field, kind] of Object.entries(kinds) as [keyof CosmeticsDto, string][]) {
      const code = dto[field];
      if (!code) continue;
      const owned = await this.prisma.userCollectible.findFirst({ where: { userId, collectible: { code, kind: kind as never } } });
      if (!owned) throw badRequest('NOT_OWNED', 'Collect this item before equipping it');
    }
    await this.prisma.userProfile.update({ where: { userId }, data: { avatarFrameKey: dto.avatarFrameKey, titleKey: dto.titleKey, markerKey: dto.markerKey } });
    return this.me(userId);
  }

  async updatePrivacy(userId: string, dto: PrivacyDto) {
    const before = await this.prisma.userPrivacySettings.findUniqueOrThrow({ where: { userId } });
    const after = await this.prisma.userPrivacySettings.update({ where: { userId }, data: dto });
    if (before.historyEnabled && !after.historyEnabled) await this.prisma.locationHistory.deleteMany({ where: { userId } });
    if (before.explorationEnabled !== after.explorationEnabled) {
      await this.prisma.auditEvent.create({ data: { actorId: userId, action: after.explorationEnabled ? 'exploration.enable' : 'exploration.disable' } });
      await this.sharing.afterSharingChange(userId);
    }
    return this.privacyView(after);
  }

  async block(userId: string, targetId: string) {
    if (userId === targetId) throw badRequest('INVALID_TARGET', 'You cannot block yourself');
    if (!(await this.prisma.user.findUnique({ where: { id: targetId } }))) throw notFound('User');
    await this.prisma.userBlock.upsert({ where: { blockerId_blockedId: { blockerId: userId, blockedId: targetId } }, create: { blockerId: userId, blockedId: targetId }, update: {} });
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'user.block', targetType: 'user', targetId } });
    // Both sides stop seeing each other immediately.
    for (const id of [userId, targetId]) await this.sharing.announce(id, await this.access.circleIdsOf(id));
  }

  async unblock(userId: string, targetId: string) {
    await this.prisma.userBlock.deleteMany({ where: { blockerId: userId, blockedId: targetId } });
    for (const id of [userId, targetId]) await this.sharing.announce(id, await this.access.circleIdsOf(id));
  }

  async blocks(userId: string) {
    const rows = await this.prisma.userBlock.findMany({ where: { blockerId: userId }, include: { blocked: { include: { profile: true } } } });
    return rows.map((b) => ({ userId: b.blockedId, username: b.blocked.username, displayName: b.blocked.profile?.displayName, since: b.createdAt }));
  }

  async report(userId: string, targetId: string, reason: string, details?: string, circleId?: string) {
    if (userId === targetId) throw badRequest('INVALID_TARGET', 'You cannot report yourself');
    const mates = await this.access.circleMates(userId);
    // Reports are about people you actually share a circle with (or did, if they blocked you).
    if (!mates.includes(targetId) && !(await this.prisma.userBlock.findFirst({ where: { OR: [{ blockerId: userId, blockedId: targetId }, { blockerId: targetId, blockedId: userId }] } }))) {
      throw notFound('User');
    }
    const r = await this.prisma.abuseReport.create({ data: { reporterId: userId, reportedId: targetId, reason, details, circleId } });
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'user.report', targetType: 'report', targetId: r.id } });
    return { id: r.id, status: r.status };
  }

  async registerDevice(userId: string, d: { installId: string; model?: string; appVersion?: string }) {
    await this.prisma.deviceRegistration.upsert({
      where: { userId_installId: { userId, installId: d.installId } },
      create: { userId, ...d },
      update: { model: d.model, appVersion: d.appVersion, lastSeenAt: new Date() },
    });
  }

  /** Everything we hold about the user, as JSON. Other people's coordinates are never included. */
  async export(userId: string) {
    const u = await this.prisma.user.findUniqueOrThrow({
      where: { id: userId },
      include: {
        profile: true,
        privacy: true,
        sessions: { select: { deviceName: true, userAgent: true, createdAt: true, lastUsedAt: true, revokedAt: true } },
        memberships: { include: { circle: { select: { id: true, name: true } } } },
        sharingSessions: true,
        latestLocation: true,
        locationHistory: { orderBy: { recordedAt: 'asc' } },
        placesCreated: true,
        geofenceEvents: true,
        quests: { include: { quest: { select: { code: true, name: true } } } },
        xpTransactions: true,
        achievements: { include: { achievement: { select: { code: true, name: true } } } },
        collectibles: { include: { collectible: { select: { code: true, name: true } } } },
        tileDiscoveries: true,
        landmarkVisits: { include: { landmark: { select: { code: true, name: true } } } },
        notifications: true,
        devices: true,
      },
    });
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'user.export' } });
    const { passwordHash: _ph, googleSub: _gs, ...rest } = u;
    return JSON.parse(JSON.stringify({ exportedAt: new Date(), user: rest }, (_, v) => (typeof v === 'bigint' ? v.toString() : v)));
  }

  /**
   * Deletes the account now. Ownership of shared circles moves to another member; every row
   * that belongs to the user (locations, history, game progress, sessions) goes with the user
   * row through cascading foreign keys. Audit events keep only the user id.
   */
  async deleteAccount(userId: string, password?: string) {
    const u = await this.prisma.user.findUniqueOrThrow({ where: { id: userId } });
    if (u.passwordHash && !(await verifyPassword(u.passwordHash, password ?? ''))) {
      throw new ApiError(HttpStatus.FORBIDDEN, 'WRONG_PASSWORD', 'Enter your password to delete the account');
    }
    await this.sharing.stopAll(userId, 'ACCOUNT_DELETED');
    for (const circleId of await this.access.circleIdsOf(userId)) await this.circles.leave(circleId, userId);
    const sessions = await this.prisma.userSession.findMany({ where: { userId }, select: { id: true } });
    await this.prisma.$transaction([
      this.prisma.auditEvent.create({ data: { actorId: userId, action: 'user.delete' } }),
      this.prisma.user.delete({ where: { id: userId } }),
    ]);
    this.tokens.forget(sessions.map((s) => s.id));
  }
}
