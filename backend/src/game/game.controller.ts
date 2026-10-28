import { Body, Controller, Get, Headers, HttpCode, Param, ParseUUIDPipe, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiHeader, ApiTags } from '@nestjs/swagger';
import { CircleAccessService } from '../circles/circle-access.service';
import { ChallengeDto } from '../circles/circles.dto';
import { AuthUser, CurrentUser } from '../common/auth-context';
import { badRequest } from '../common/errors';
import { RateLimit } from '../common/rate-limit';
import { PrismaService } from '../prisma/prisma.service';
import { AchievementsService } from './achievements.service';
import { ChallengesService } from './challenges.service';
import { ExplorationService } from './exploration.service';
import { BoardMetric, BoardPeriod, BoardScope, LeaderboardService } from './leaderboard.service';
import { LevelService } from './level.service';
import { QuestsService } from './quests.service';
import { TerritoryService } from './territory.service';

const uuid = new ParseUUIDPipe({ version: '4' });

function num(v: string | undefined, name: string, min: number, max: number): number {
  const n = Number(v);
  if (v === undefined || !Number.isFinite(n) || n < min || n > max) throw badRequest('INVALID_QUERY', `${name} must be between ${min} and ${max}`);
  return n;
}

function bounds(q: Record<string, string | undefined>) {
  return {
    north: num(q.north, 'north', -90, 90),
    south: num(q.south, 'south', -90, 90),
    east: num(q.east, 'east', -180, 180),
    west: num(q.west, 'west', -180, 180),
  };
}

@ApiTags('game')
@ApiBearerAuth()
@Controller()
export class GameController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly quests: QuestsService,
    private readonly achievements: AchievementsService,
    private readonly exploration: ExplorationService,
    private readonly territories: TerritoryService,
    private readonly leaderboards: LeaderboardService,
    private readonly challenges: ChallengesService,
    private readonly levels: LevelService,
    private readonly access: CircleAccessService,
  ) {}

  @Get('quests')
  questList(@CurrentUser() u: AuthUser) {
    return this.quests.list(u.id);
  }

  @Get('quests/history')
  questHistory(@CurrentUser() u: AuthUser, @Query('cursor') cursor?: string) {
    return this.quests.history(u.id, cursor);
  }

  @Get('quests/:id')
  quest(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.quests.get(u.id, id);
  }

  @Post('quests/:id/claim')
  @HttpCode(200)
  @ApiHeader({ name: 'Idempotency-Key', required: true, description: 'Random per claim attempt; reuse it when retrying the same claim' })
  @RateLimit({ name: 'claim', limit: 60, windowSec: 300 })
  claim(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Headers('idempotency-key') key?: string) {
    return this.quests.claim(u.id, id, key ?? '');
  }

  @Get('achievements')
  achievementList(@CurrentUser() u: AuthUser) {
    return this.achievements.list(u.id);
  }

  @Get('collectibles')
  async collectibles(@CurrentUser() u: AuthUser) {
    const [defs, mine] = await Promise.all([
      this.prisma.collectibleDefinition.findMany({ orderBy: [{ kind: 'asc' }, { rarity: 'asc' }, { name: 'asc' }] }),
      this.prisma.userCollectible.findMany({ where: { userId: u.id } }),
    ]);
    const owned = new Map(mine.map((m) => [m.collectibleId, m]));
    return defs.map((d) => ({
      code: d.code,
      name: d.name,
      description: d.description,
      kind: d.kind,
      rarity: d.rarity,
      setCode: d.setCode,
      iconKey: d.iconKey,
      acquiredAt: owned.get(d.id)?.acquiredAt ?? null,
    }));
  }

  @Get('levels')
  async levelCurve() {
    return (await this.levels.curve()).map((l) => ({ level: l.level, totalXp: l.totalXp, rewardCollectibleCode: l.rewardCollectibleCode }));
  }

  @Get('leaderboards')
  board(
    @CurrentUser() u: AuthUser,
    @Query('scope') scope: BoardScope = 'friends',
    @Query('metric') metric: BoardMetric = 'xp',
    @Query('period') period: BoardPeriod = 'week',
    @Query('circleId') circleId?: string,
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
  ) {
    if (!['friends', 'circle', 'global'].includes(scope)) throw badRequest('INVALID_QUERY', 'scope must be friends, circle or global');
    if (!['xp', 'tiles', 'quests'].includes(metric)) throw badRequest('INVALID_QUERY', 'metric must be xp, tiles or quests');
    if (!['week', 'season'].includes(period)) throw badRequest('INVALID_QUERY', 'period must be week or season');
    return this.leaderboards.board(u.id, {
      scope,
      metric,
      period,
      circleId,
      limit: limit ? num(limit, 'limit', 1, 100) : 50,
      offset: offset ? num(offset, 'offset', 0, 10_000) : 0,
    });
  }

  @Get('leaderboards/history')
  boardHistory(@Query('board') board: string, @Query('periodKey') periodKey: string) {
    if (!board || !periodKey) throw badRequest('INVALID_QUERY', 'board and periodKey are required');
    return this.leaderboards.history(board, periodKey);
  }

  @Get('exploration/tiles')
  tiles(@CurrentUser() u: AuthUser, @Query() q: Record<string, string>) {
    return this.exploration.myTiles(u.id, bounds(q));
  }

  @Get('exploration/stats')
  stats(@CurrentUser() u: AuthUser) {
    return this.exploration.stats(u.id);
  }

  @Get('circles/:id/exploration')
  async circleExploration(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    await this.access.requireMember(id, u.id);
    return this.exploration.circleStats(id);
  }

  @Get('landmarks')
  landmarks(@CurrentUser() u: AuthUser, @Query('lat') lat?: string, @Query('lon') lon?: string, @Query('radiusM') radius?: string, @Query('q') query?: string) {
    const q = query?.trim().slice(0, 60) || undefined;
    return this.exploration.landmarksNear(
      u.id,
      num(lat, 'lat', -90, 90),
      num(lon, 'lon', -180, 180),
      radius ? num(radius, 'radiusM', 100, 50_000) : 5_000,
      q,
    );
  }

  @Get('territories')
  territoriesIn(@CurrentUser() u: AuthUser, @Query() q: Record<string, string>) {
    return this.territories.inBounds(u.id, bounds(q));
  }

  @Get('territories/:id')
  territory(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.territories.detail(u.id, id);
  }

  @Get('events')
  async events() {
    const now = new Date();
    const rows = await this.prisma.gameEvent.findMany({ where: { endsAt: { gt: now } }, orderBy: { startsAt: 'asc' }, take: 20 });
    return rows.map((e) => ({ ...e, live: e.startsAt <= now }));
  }

  @Get('circles/:id/challenges')
  challengeList(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.challenges.list(id, u.id);
  }

  @Post('circles/:id/challenges')
  @RateLimit({ name: 'challenge-create', limit: 20, windowSec: 86_400 })
  challengeCreate(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: ChallengeDto) {
    return this.challenges.create(id, u.id, dto);
  }
}
