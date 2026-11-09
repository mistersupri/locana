import { Body, Controller, Req, Res, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post, Put, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { IsArray, IsOptional, IsUUID } from 'class-validator';
import { ReportDto } from '../circles/circles.dto';
import { AuthUser, CurrentUser } from '../common/auth-context';
import { RateLimit } from '../common/rate-limit';
import { NotificationsService } from '../notifications/notifications.service';
import { CosmeticsDto, DeleteAccountDto, DeviceDto, PrivacyDto, UpdateProfileDto } from './users.dto';
import { UsersService } from './users.service';

class MarkReadDto {
  @IsOptional()
  @IsArray()
  @IsUUID('4', { each: true })
  ids?: string[];
}

const uuid = new ParseUUIDPipe({ version: '4' });

@ApiTags('me')
@ApiBearerAuth()
@Controller()
export class UsersController {
  constructor(private readonly users: UsersService, private readonly notifications: NotificationsService) {}

  @Get('me')
  me(@CurrentUser() u: AuthUser) {
    return this.users.me(u.id);
  }

  @Patch('me')
  update(@CurrentUser() u: AuthUser, @Body() dto: UpdateProfileDto) {
    return this.users.updateProfile(u.id, dto);
  }

  @Put('me/avatar-photo')
  @RateLimit({ name: 'avatar-photo', limit: 20, windowSec: 600 })
  setPhoto(@CurrentUser() u: AuthUser, @Req() req: { body: unknown }) {
    return this.users.setPhoto(u.id, req.body);
  }

  @Delete('me/avatar-photo')
  removePhoto(@CurrentUser() u: AuthUser) {
    return this.users.removePhoto(u.id);
  }

  @Get('users/:id/avatar-photo')
  async photo(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Res() res: { setHeader(k: string, v: string): void; end(b: Buffer): void }) {
    const p = await this.users.photoFor(u.id, id);
    res.setHeader('Content-Type', p.contentType);
    res.setHeader('Cache-Control', 'private, max-age=86400');
    res.end(Buffer.from(p.data));
  }

  @Put('me/cosmetics')
  cosmetics(@CurrentUser() u: AuthUser, @Body() dto: CosmeticsDto) {
    return this.users.setCosmetics(u.id, dto);
  }

  @Patch('me/privacy')
  privacy(@CurrentUser() u: AuthUser, @Body() dto: PrivacyDto) {
    return this.users.updatePrivacy(u.id, dto);
  }

  @Post('me/devices')
  @HttpCode(204)
  device(@CurrentUser() u: AuthUser, @Body() dto: DeviceDto) {
    return this.users.registerDevice(u.id, dto);
  }

  @Get('me/export')
  @RateLimit({ name: 'export', limit: 5, windowSec: 3600 })
  export(@CurrentUser() u: AuthUser) {
    return this.users.export(u.id);
  }

  @Post('me/delete')
  @HttpCode(204)
  @RateLimit({ name: 'delete-account', limit: 5, windowSec: 3600 })
  remove(@CurrentUser() u: AuthUser, @Body() dto: DeleteAccountDto) {
    return this.users.deleteAccount(u.id, dto.password);
  }

  @Get('me/blocks')
  blocks(@CurrentUser() u: AuthUser) {
    return this.users.blocks(u.id);
  }

  @Put('users/:id/block')
  @HttpCode(204)
  block(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.users.block(u.id, id);
  }

  @Delete('users/:id/block')
  @HttpCode(204)
  unblock(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.users.unblock(u.id, id);
  }

  @Post('users/:id/report')
  @RateLimit({ name: 'report', limit: 20, windowSec: 86_400 })
  report(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: ReportDto) {
    return this.users.report(u.id, id, dto.reason, dto.details, dto.circleId);
  }

  @Get('notifications')
  notificationsList(@CurrentUser() u: AuthUser, @Query('cursor') cursor?: string, @Query('limit') limit?: string) {
    return this.notifications.list(u.id, cursor, limit ? Math.max(1, Number(limit)) : undefined);
  }

  @Post('notifications/read')
  @HttpCode(204)
  markRead(@CurrentUser() u: AuthUser, @Body() dto: MarkReadDto) {
    return this.notifications.markRead(u.id, dto.ids ?? 'all');
  }
}
