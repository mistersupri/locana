import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post, Put, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { IsIn, IsOptional } from 'class-validator';
import { AuthUser, CurrentUser } from '../common/auth-context';
import { RateLimit } from '../common/rate-limit';
import { CreateCircleDto, CreateInvitationDto, JoinCircleDto, MemberPrefsDto, SetRoleDto, UpdateCircleDto, VisibilityDto } from './circles.dto';
import { CirclesService } from './circles.service';
import { FeedService, REACTIONS } from './feed.service';

class ReactDto {
  @IsOptional()
  @IsIn([...REACTIONS, null])
  emoji: string | null;
}

const uuid = new ParseUUIDPipe({ version: '4' });

@ApiTags('circles')
@ApiBearerAuth()
@Controller('circles')
export class CirclesController {
  constructor(private readonly circles: CirclesService, private readonly feed: FeedService) {}

  @Get()
  list(@CurrentUser() u: AuthUser) {
    return this.circles.list(u.id);
  }

  @Post()
  @RateLimit({ name: 'circle-create', limit: 20, windowSec: 3600 })
  create(@CurrentUser() u: AuthUser, @Body() dto: CreateCircleDto) {
    return this.circles.create(u.id, dto);
  }

  @Post('join')
  @HttpCode(200)
  // Codes carry ~49 bits; this limit makes guessing hopeless and still forgives typos.
  @RateLimit({ name: 'circle-join', limit: 10, windowSec: 3600 })
  join(@CurrentUser() u: AuthUser, @Body() dto: JoinCircleDto) {
    return this.circles.join(u.id, dto.code);
  }

  @Get(':id')
  get(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.circles.get(id, u.id);
  }

  @Patch(':id')
  update(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: UpdateCircleDto) {
    return this.circles.update(id, u.id, dto);
  }

  @Delete(':id')
  @HttpCode(204)
  remove(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.circles.remove(id, u.id);
  }

  @Post(':id/leave')
  @HttpCode(204)
  leave(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.circles.leave(id, u.id);
  }

  @Get(':id/invitations')
  invitations(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.circles.listInvitations(id, u.id);
  }

  @Post(':id/invitations')
  @RateLimit({ name: 'invite-create', limit: 30, windowSec: 3600 })
  createInvitation(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: CreateInvitationDto) {
    return this.circles.createInvitation(id, u.id, dto);
  }

  @Delete(':id/invitations/:invitationId')
  @HttpCode(204)
  revokeInvitation(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Param('invitationId', uuid) invitationId: string) {
    return this.circles.revokeInvitation(id, invitationId, u.id);
  }

  @Put(':id/members/:userId/role')
  setRole(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Param('userId', uuid) userId: string, @Body() dto: SetRoleDto) {
    return this.circles.setRole(id, u.id, userId, dto.role);
  }

  @Delete(':id/members/:userId')
  @HttpCode(204)
  removeMember(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Param('userId', uuid) userId: string) {
    return this.circles.removeMember(id, u.id, userId);
  }

  @Patch(':id/me')
  @HttpCode(204)
  prefs(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: MemberPrefsDto) {
    if (dto.muted !== undefined) return this.circles.setMuted(id, u.id, dto.muted);
  }

  @Put(':id/visibility')
  @HttpCode(204)
  visibility(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: VisibilityDto) {
    return this.circles.setHiddenFrom(id, u.id, dto.viewerId, dto.hidden);
  }

  @Get(':id/feed')
  feedList(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Query('cursor') cursor?: string, @Query('limit') limit?: string) {
    return this.feed.list(id, u.id, cursor, limit ? Number(limit) : undefined);
  }

  @Put('feed/:itemId/reaction')
  @HttpCode(204)
  react(@CurrentUser() u: AuthUser, @Param('itemId', uuid) itemId: string, @Body() dto: ReactDto) {
    return this.feed.react(itemId, u.id, dto.emoji ?? null);
  }
}
