import { Inject, Injectable, Logger } from '@nestjs/common';
import { OAuth2Client } from 'google-auth-library';
import { APP_ENV, AppEnv } from '../config/env';
import { humanCode, normalizeCode, sha256 } from '../common/crypto';
import { ApiError, badRequest, conflict } from '../common/errors';
import { PrismaService } from '../prisma/prisma.service';
import { UsersService } from '../users/users.service';
import { LoginDto, RegisterDto, ResetPasswordDto } from './auth.dto';
import { unauthenticated } from './auth.errors';
import { MailService } from './mail.service';
import { NotificationsService } from '../notifications/notifications.service';
import { hashPassword, verifyPassword } from './passwords';
import { TokenService } from './token.service';

interface DeviceInfo {
  deviceName?: string;
  userAgent?: string;
}

const RESET_TTL_MS = 15 * 60_000;

@Injectable()
export class AuthService {
  private readonly log = new Logger('Auth');
  private readonly google: OAuth2Client | null;

  constructor(
    private readonly prisma: PrismaService,
    private readonly tokens: TokenService,
    private readonly users: UsersService,
    private readonly mail: MailService,
    private readonly notifications: NotificationsService,
    @Inject(APP_ENV) private readonly env: AppEnv,
  ) {
    this.google = env.googleClientId ? new OAuth2Client(env.googleClientId) : null;
  }

  async register(dto: RegisterDto, device: DeviceInfo) {
    const taken = await this.prisma.user.findFirst({ where: { OR: [{ email: dto.email }, { username: dto.username }] }, select: { email: true } });
    if (taken) {
      throw taken.email === dto.email
        ? conflict('EMAIL_TAKEN', 'An account with this email already exists')
        : conflict('USERNAME_TAKEN', 'That username is taken');
    }
    const passwordHash = await hashPassword(dto.password);
    const { tokens, userId } = await this.prisma.$transaction(async (tx) => {
      const user = await tx.user.create({
        data: {
          email: dto.email,
          username: dto.username,
          passwordHash,
          // Privacy rows start with every sharing and game feature off. The user turns them on.
          profile: { create: { displayName: dto.displayName } },
          privacy: { create: {} },
        },
      });
      await tx.auditEvent.create({ data: { actorId: user.id, action: 'auth.register' } });
      const issued = await this.tokens.issue(user.id, { deviceName: dto.deviceName ?? device.deviceName, userAgent: device.userAgent }, tx);
      return { tokens: issued, userId: user.id };
    });
    return { tokens, user: await this.users.me(userId) };
  }

  async login(dto: LoginDto, device: DeviceInfo) {
    const user = await this.prisma.user.findFirst({
      where: { OR: [{ email: dto.identifier }, { username: dto.identifier }], deletedAt: null },
    });
    const ok = await verifyPassword(user?.passwordHash, dto.password);
    if (!user || !ok) throw unauthenticated('INVALID_CREDENTIALS');
    const tokens = await this.tokens.issue(user.id, { deviceName: dto.deviceName ?? device.deviceName, userAgent: device.userAgent });
    await this.prisma.auditEvent.create({ data: { actorId: user.id, action: 'auth.login', targetType: 'session', targetId: tokens.sessionId } });
    return { tokens, user: await this.users.me(user.id) };
  }

  refresh(refreshToken: string, device: DeviceInfo) {
    return this.tokens.rotate(refreshToken, device);
  }

  async logout(sessionId: string) {
    await this.tokens.revokeFamily(sessionId);
  }

  async logoutOthers(userId: string, sessionId: string) {
    await this.tokens.revokeAll(userId, sessionId);
  }

  /** Always succeeds from the caller's view, so it cannot be used to discover accounts. */
  async forgotPassword(email: string) {
    const user = await this.prisma.user.findFirst({ where: { email, deletedAt: null } });
    if (!user || !user.passwordHash) return;
    const code = humanCode(8);
    await this.prisma.passwordResetToken.updateMany({ where: { userId: user.id, usedAt: null }, data: { usedAt: new Date() } });
    await this.prisma.passwordResetToken.create({ data: { userId: user.id, codeHash: sha256(`${user.id}:${code}`), expiresAt: new Date(Date.now() + RESET_TTL_MS) } });
    const pretty = `${code.slice(0, 4)}-${code.slice(4)}`;
    await this.mail.send(
      email,
      'Your Locana reset code',
      `Use this code to choose a new Locana password: ${pretty}\n\nIt works for 15 minutes. If you did not ask for it, ignore this email; your password stays the same.`,
    );
  }

  async resetPassword(dto: ResetPasswordDto) {
    const user = await this.prisma.user.findFirst({ where: { email: dto.email, deletedAt: null } });
    const invalid = badRequest('INVALID_RESET_CODE', 'That code is wrong or has expired');
    if (!user) throw invalid;
    const token = await this.prisma.passwordResetToken.findUnique({ where: { codeHash: sha256(`${user.id}:${normalizeCode(dto.code)}`) } });
    if (!token || token.userId !== user.id || token.usedAt || token.expiresAt <= new Date()) throw invalid;
    const passwordHash = await hashPassword(dto.newPassword);
    await this.prisma.$transaction([
      this.prisma.passwordResetToken.update({ where: { id: token.id }, data: { usedAt: new Date() } }),
      this.prisma.user.update({ where: { id: user.id }, data: { passwordHash } }),
      this.prisma.auditEvent.create({ data: { actorId: user.id, action: 'auth.password_reset' } }),
    ]);
    await this.tokens.revokeAll(user.id);
    await this.notifications.notify(user.id, 'SECURITY', 'Password changed', 'Your password was reset and every device was signed out.');
  }

  async changePassword(userId: string, sessionId: string, current: string, next: string) {
    const user = await this.prisma.user.findUniqueOrThrow({ where: { id: userId } });
    if (!(await verifyPassword(user.passwordHash, current))) throw new ApiError(403, 'WRONG_PASSWORD', 'Current password is incorrect');
    await this.prisma.user.update({ where: { id: userId }, data: { passwordHash: await hashPassword(next) } });
    await this.tokens.revokeAll(userId, sessionId);
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'auth.password_change' } });
    await this.notifications.notify(userId, 'SECURITY', 'Password changed', 'Your password was changed. Other devices were signed out.');
  }

  async googleSignIn(idToken: string, device: DeviceInfo) {
    if (!this.google || !this.env.googleClientId) throw new ApiError(501, 'GOOGLE_SIGN_IN_DISABLED', 'Google sign-in is not configured on this server');
    let payload;
    try {
      const ticket = await this.google.verifyIdToken({ idToken, audience: this.env.googleClientId });
      payload = ticket.getPayload();
    } catch (e) {
      this.log.warn(`Google token rejected: ${(e as Error).message}`);
      throw unauthenticated('INVALID_GOOGLE_TOKEN');
    }
    if (!payload?.sub || !payload.email || payload.email_verified !== true) throw unauthenticated('INVALID_GOOGLE_TOKEN');
    const email = payload.email.toLowerCase();
    let user = await this.prisma.user.findUnique({ where: { googleSub: payload.sub } });
    if (!user) {
      const byEmail = await this.prisma.user.findUnique({ where: { email } });
      if (byEmail) {
        // Google verified ownership of this address, so linking is safe.
        user = await this.prisma.user.update({ where: { id: byEmail.id }, data: { googleSub: payload.sub } });
      } else {
        user = await this.prisma.user.create({
          data: {
            email,
            googleSub: payload.sub,
            username: await this.users.freeUsername(email.split('@')[0]),
            profile: { create: { displayName: (payload.given_name ?? payload.name ?? email.split('@')[0]).slice(0, 40) } },
            privacy: { create: {} },
          },
        });
      }
    }
    if (user.deletedAt) throw unauthenticated('INVALID_CREDENTIALS');
    const tokens = await this.tokens.issue(user.id, device);
    return { tokens, user: await this.users.me(user.id) };
  }
}
