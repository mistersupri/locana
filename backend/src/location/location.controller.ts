import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Post, Put, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { IsBoolean } from 'class-validator';
import { AuthUser, CurrentUser } from '../common/auth-context';
import { badRequest } from '../common/errors';
import { RateLimit } from '../common/rate-limit';
import { StartSharingDto, UploadLocationsDto } from './location.dto';
import { LocationService } from './location.service';
import { SharingService } from './sharing.service';

class PauseAllDto {
  @IsBoolean()
  paused: boolean;
}

const uuid = new ParseUUIDPipe({ version: '4' });
const optDate = (v?: string) => {
  if (!v) return undefined;
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) throw badRequest('INVALID_DATE', `Not a date: ${v}`);
  return d;
};

@ApiTags('location')
@ApiBearerAuth()
@Controller()
export class LocationController {
  constructor(private readonly location: LocationService, private readonly sharing: SharingService) {}

  @Post('location')
  @HttpCode(200)
  // Generous for batched retries after a long offline stretch; still stops runaway clients.
  @RateLimit({ name: 'location-upload', limit: 240, windowSec: 600 })
  upload(@CurrentUser() u: AuthUser, @Body() dto: UploadLocationsDto) {
    return this.location.ingest(u.id, dto.samples);
  }

  @Get('circles/:id/locations')
  circleLocations(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.location.circleLocations(id, u.id);
  }

  @Get('circles/:id/members/:userId/timeline')
  timeline(
    @CurrentUser() u: AuthUser,
    @Param('id', uuid) id: string,
    @Param('userId', uuid) userId: string,
    @Query('day') day?: string,
    @Query('tzOffsetMinutes') tz?: string,
  ) {
    const offset = Number(tz ?? 0);
    if (!Number.isInteger(offset) || Math.abs(offset) > 14 * 60) throw badRequest('INVALID_TZ', 'tzOffsetMinutes must be whole minutes between -840 and 840');
    const d = day ?? new Date(Date.now() + offset * 60_000).toISOString().slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(d) || Number.isNaN(Date.parse(`${d}T00:00:00Z`))) throw badRequest('INVALID_DATE', 'day must look like 2026-11-08');
    return this.location.timeline(id, u.id, userId, d, offset);
  }

  @Get('sharing')
  state(@CurrentUser() u: AuthUser) {
    return this.sharing.state(u.id);
  }

  @Post('sharing')
  start(@CurrentUser() u: AuthUser, @Body() dto: StartSharingDto) {
    return this.sharing.start(u.id, dto.circleId, dto.durationMinutes);
  }

  @Post('sharing/:id/pause')
  @HttpCode(200)
  pause(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.sharing.pause(u.id, id);
  }

  @Post('sharing/:id/resume')
  @HttpCode(200)
  resume(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.sharing.resume(u.id, id);
  }

  @Post('sharing/:id/stop')
  @HttpCode(200)
  stop(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.sharing.stop(u.id, id);
  }

  @Put('sharing/pause-all')
  pauseAll(@CurrentUser() u: AuthUser, @Body() dto: PauseAllDto) {
    return this.sharing.setGlobalPause(u.id, dto.paused);
  }

  @Post('sharing/stop-all')
  @HttpCode(200)
  async stopAll(@CurrentUser() u: AuthUser) {
    await this.sharing.stopAll(u.id, 'STOPPED');
    return this.sharing.state(u.id);
  }

  @Get('me/location-history')
  history(@CurrentUser() u: AuthUser, @Query('from') from?: string, @Query('to') to?: string) {
    return this.location.history(u.id, optDate(from), optDate(to));
  }

  @Delete('me/location-history')
  deleteHistory(@CurrentUser() u: AuthUser, @Query('before') before?: string) {
    return this.location.deleteHistory(u.id, optDate(before));
  }
}
