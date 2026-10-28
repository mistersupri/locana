import { Body, Controller, Get, HttpCode, Post, Req } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { Request } from 'express';
import { AuthUser, CurrentUser, Public } from '../common/auth-context';
import { RateLimit } from '../common/rate-limit';
import { PrismaService } from '../prisma/prisma.service';
import { ChangePasswordDto, ForgotPasswordDto, GoogleSignInDto, LoginDto, RefreshDto, RegisterDto, ResetPasswordDto } from './auth.dto';
import { AuthService } from './auth.service';

const device = (req: Request, deviceName?: string) => ({ deviceName, userAgent: req.headers['user-agent'] });

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService, private readonly prisma: PrismaService) {}

  @Public()
  @Post('register')
  @RateLimit({ name: 'register', limit: 10, windowSec: 3600, by: 'ip' })
  register(@Body() dto: RegisterDto, @Req() req: Request) {
    return this.auth.register(dto, device(req, dto.deviceName));
  }

  @Public()
  @Post('login')
  @HttpCode(200)
  @RateLimit({ name: 'login', limit: 20, windowSec: 900, by: 'ip' })
  login(@Body() dto: LoginDto, @Req() req: Request) {
    return this.auth.login(dto, device(req, dto.deviceName));
  }

  @Public()
  @Post('google')
  @HttpCode(200)
  @RateLimit({ name: 'login', limit: 20, windowSec: 900, by: 'ip' })
  google(@Body() dto: GoogleSignInDto, @Req() req: Request) {
    return this.auth.googleSignIn(dto.idToken, device(req, dto.deviceName));
  }

  @Public()
  @Post('refresh')
  @HttpCode(200)
  @RateLimit({ name: 'refresh', limit: 60, windowSec: 300, by: 'ip' })
  refresh(@Body() dto: RefreshDto, @Req() req: Request) {
    return this.auth.refresh(dto.refreshToken, device(req));
  }

  @Public()
  @Post('password/forgot')
  @HttpCode(202)
  @RateLimit({ name: 'pw-forgot', limit: 5, windowSec: 3600, by: 'ip' })
  async forgot(@Body() dto: ForgotPasswordDto) {
    await this.auth.forgotPassword(dto.email);
    return { status: 'sent_if_account_exists' };
  }

  @Public()
  @Post('password/reset')
  @HttpCode(200)
  @RateLimit({ name: 'pw-reset', limit: 10, windowSec: 3600, by: 'ip' })
  async reset(@Body() dto: ResetPasswordDto) {
    await this.auth.resetPassword(dto);
    return { status: 'password_changed' };
  }

  @ApiBearerAuth()
  @Post('password/change')
  @HttpCode(200)
  @RateLimit({ name: 'pw-change', limit: 10, windowSec: 3600 })
  async change(@CurrentUser() u: AuthUser, @Body() dto: ChangePasswordDto) {
    await this.auth.changePassword(u.id, u.sessionId, dto.currentPassword, dto.newPassword);
    return { status: 'password_changed' };
  }

  @ApiBearerAuth()
  @Post('logout')
  @HttpCode(204)
  async logout(@CurrentUser() u: AuthUser) {
    await this.auth.logout(u.sessionId);
  }

  @ApiBearerAuth()
  @Post('logout-others')
  @HttpCode(204)
  async logoutOthers(@CurrentUser() u: AuthUser) {
    await this.auth.logoutOthers(u.id, u.sessionId);
  }

  @ApiBearerAuth()
  @Get('sessions')
  async sessions(@CurrentUser() u: AuthUser) {
    const current = await this.prisma.userSession.findUnique({ where: { id: u.sessionId } });
    const rows = await this.prisma.userSession.findMany({
      where: { userId: u.id, revokedAt: null, rotatedAt: null, expiresAt: { gt: new Date() } },
      orderBy: { lastUsedAt: 'desc' },
    });
    return rows.map((s) => ({
      id: s.id,
      deviceName: s.deviceName,
      userAgent: s.userAgent,
      createdAt: s.createdAt,
      lastUsedAt: s.lastUsedAt,
      current: s.familyId === current?.familyId,
    }));
  }

  @ApiBearerAuth()
  @Post('sessions/:id/revoke')
  @HttpCode(204)
  async revokeSession(@CurrentUser() u: AuthUser, @Req() req: Request) {
    const id = String(req.params.id);
    const s = await this.prisma.userSession.findFirst({ where: { id, userId: u.id } });
    if (s) await this.auth.logout(s.id);
  }
}
