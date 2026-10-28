import { Inject, Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { randomUUID } from 'crypto';
import { APP_ENV, AppEnv } from '../config/env';
import { opaqueToken, sha256 } from '../common/crypto';
import { unauthenticated } from './auth.errors';
import { PrismaService, Tx } from '../prisma/prisma.service';

export interface TokenPair {
  accessToken: string;
  accessTokenExpiresIn: number;
  refreshToken: string;
  sessionId: string;
}

interface AccessClaims {
  sub: string;
  sid: string;
}

/**
 * Short-lived JWT access tokens + opaque rotating refresh tokens.
 *
 * Each refresh rotates the token: the old row is marked rotated and a new row joins the same
 * family. Presenting an already-rotated token means it was copied, so the whole family
 * (that login on that device) is revoked.
 */
@Injectable()
export class TokenService {
  // sessionId -> { active, checkedAt }. Short TTL so logout takes effect within seconds.
  private readonly sessionCache = new Map<string, { active: boolean; at: number }>();
  private static readonly CACHE_MS = 10_000;

  constructor(private readonly jwt: JwtService, private readonly prisma: PrismaService, @Inject(APP_ENV) private readonly env: AppEnv) {}

  async issue(userId: string, device: { deviceName?: string; userAgent?: string }, tx: Tx = this.prisma, familyId: string = randomUUID()): Promise<TokenPair> {
    const refreshToken = opaqueToken();
    const session = await tx.userSession.create({
      data: {
        userId,
        familyId,
        refreshTokenHash: sha256(refreshToken),
        deviceName: device.deviceName?.slice(0, 80),
        userAgent: device.userAgent?.slice(0, 200),
        expiresAt: new Date(Date.now() + this.env.refreshTokenTtlDays * 86_400_000),
      },
    });
    return { ...this.access(userId, session.id), refreshToken, sessionId: session.id };
  }

  private access(userId: string, sessionId: string) {
    const claims: AccessClaims = { sub: userId, sid: sessionId };
    const accessToken = this.jwt.sign(claims, { expiresIn: this.env.accessTokenTtlSeconds });
    return { accessToken, accessTokenExpiresIn: this.env.accessTokenTtlSeconds };
  }

  async rotate(refreshToken: string, device: { userAgent?: string }): Promise<TokenPair> {
    const hash = sha256(refreshToken);
    const s = await this.prisma.userSession.findUnique({ where: { refreshTokenHash: hash } });
    if (!s || s.revokedAt) throw unauthenticated('INVALID_REFRESH_TOKEN');
    if (s.rotatedAt) {
      // Reuse of a rotated token: assume it was copied, end every session in the family.
      await this.revokeFamily(s.id);
      await this.prisma.auditEvent.create({ data: { actorId: s.userId, action: 'auth.refresh_reuse_detected', targetType: 'session', targetId: s.id } });
      throw unauthenticated('INVALID_REFRESH_TOKEN');
    }
    if (s.expiresAt <= new Date()) throw unauthenticated('REFRESH_TOKEN_EXPIRED');
    return this.prisma.$transaction(async (tx) => {
      const user = await tx.user.findUnique({ where: { id: s.userId } });
      if (!user || user.deletedAt) throw unauthenticated('INVALID_REFRESH_TOKEN');
      // Conditional update guards against two concurrent refreshes with the same token.
      const claimed = await tx.userSession.updateMany({ where: { id: s.id, rotatedAt: null }, data: { rotatedAt: new Date() } });
      if (claimed.count !== 1) throw unauthenticated('INVALID_REFRESH_TOKEN');
      return this.issue(s.userId, { deviceName: s.deviceName ?? undefined, userAgent: device.userAgent ?? s.userAgent ?? undefined }, tx, s.familyId);
    });
  }

  async verifyAccess(token: string): Promise<{ userId: string; sessionId: string }> {
    let claims: AccessClaims;
    try {
      claims = this.jwt.verify<AccessClaims>(token);
    } catch {
      throw unauthenticated('INVALID_ACCESS_TOKEN');
    }
    if (!(await this.sessionActive(claims.sid, claims.sub))) throw unauthenticated('SESSION_REVOKED');
    return { userId: claims.sub, sessionId: claims.sid };
  }

  private async sessionActive(sessionId: string, userId: string): Promise<boolean> {
    const hit = this.sessionCache.get(sessionId);
    if (hit && Date.now() - hit.at < TokenService.CACHE_MS) return hit.active;
    // A rotated session keeps revokedAt = null, so access tokens minted before a refresh stay
    // valid until they expire (≤ 15 min). Logout revokes the whole family at once.
    const s = await this.prisma.userSession.findUnique({ where: { id: sessionId }, select: { userId: true, revokedAt: true, expiresAt: true } });
    const active = !!s && s.userId === userId && !s.revokedAt && s.expiresAt > new Date();
    this.sessionCache.set(sessionId, { active, at: Date.now() });
    if (this.sessionCache.size > 20_000) this.sessionCache.clear();
    return active;
  }

  forget(sessionIds: string[]) {
    for (const id of sessionIds) this.sessionCache.delete(id);
  }

  async revokeFamily(sessionId: string) {
    const s = await this.prisma.userSession.findUnique({ where: { id: sessionId } });
    if (!s) return;
    const sessions = await this.prisma.userSession.findMany({ where: { familyId: s.familyId }, select: { id: true } });
    await this.prisma.userSession.updateMany({ where: { familyId: s.familyId, revokedAt: null }, data: { revokedAt: new Date() } });
    this.forget(sessions.map((x) => x.id));
  }

  async revokeAll(userId: string, exceptFamilyOf?: string) {
    let keepFamily: string | undefined;
    if (exceptFamilyOf) keepFamily = (await this.prisma.userSession.findUnique({ where: { id: exceptFamilyOf } }))?.familyId;
    const sessions = await this.prisma.userSession.findMany({ where: { userId, ...(keepFamily ? { NOT: { familyId: keepFamily } } : {}) }, select: { id: true } });
    await this.prisma.userSession.updateMany({ where: { id: { in: sessions.map((s) => s.id) }, revokedAt: null }, data: { revokedAt: new Date() } });
    this.forget(sessions.map((s) => s.id));
  }
}
