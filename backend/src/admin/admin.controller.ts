import { Body, CanActivate, Controller, ExecutionContext, Get, Inject, Injectable, Param, ParseUUIDPipe, Patch, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsBoolean, IsIn, IsInt, IsLatitude, IsLongitude, IsOptional, IsString, Length, Max, Min } from 'class-validator';
import { AuthUser, CurrentUser } from '../common/auth-context';
import { forbidden } from '../common/errors';
import { APP_ENV, AppEnv } from '../config/env';
import { PrismaService } from '../prisma/prisma.service';

/** Admin routes are limited to the user ids listed in ADMIN_USER_IDS. */
@Injectable()
class AdminGuard implements CanActivate {
  constructor(@Inject(APP_ENV) private readonly env: AppEnv) {}
  canActivate(ctx: ExecutionContext) {
    const id = ctx.switchToHttp().getRequest().user?.id;
    if (!id || !this.env.adminUserIds.includes(id)) throw forbidden('ADMIN_ONLY', 'Admins only');
    return true;
  }
}

class LandmarkDto {
  @IsString() @Length(3, 40) code: string;
  @IsString() @Length(2, 80) name: string;
  @IsString() @Length(0, 300) description: string;
  @IsIn(['park', 'museum', 'monument', 'square', 'library', 'market', 'viewpoint', 'religious', 'other']) category: string;
  @IsLatitude() latitude: number;
  @IsLongitude() longitude: number;
  @IsInt() @Min(30) @Max(500) radiusM: number;
  @IsOptional() @IsString() setCode?: string;
  @IsOptional() @IsString() collectibleCode?: string;
  @IsInt() @Min(0) @Max(500) rewardXp: number;
  @IsOptional() @IsBoolean() withTerritory?: boolean;
}

class ReportStatusDto {
  @IsIn(['OPEN', 'REVIEWING', 'ACTIONED', 'DISMISSED']) status: string;
}

class ListQuery {
  @IsOptional() @Type(() => Number) @IsInt() @Min(1) @Max(200) limit?: number;
}

const uuid = new ParseUUIDPipe({ version: '4' });

@ApiTags('admin')
@ApiBearerAuth()
@UseGuards(AdminGuard)
@Controller('admin')
export class AdminController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('audit')
  audit(@Query() q: ListQuery, @Query('action') action?: string) {
    return this.prisma.auditEvent.findMany({ where: action ? { action } : {}, orderBy: { createdAt: 'desc' }, take: q.limit ?? 100 });
  }

  @Get('reports')
  reports(@Query() q: ListQuery) {
    return this.prisma.abuseReport.findMany({ orderBy: { createdAt: 'desc' }, take: q.limit ?? 100 });
  }

  @Patch('reports/:id')
  async reportStatus(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: ReportStatusDto) {
    const r = await this.prisma.abuseReport.update({ where: { id }, data: { status: dto.status } });
    await this.prisma.auditEvent.create({ data: { actorId: u.id, action: 'admin.report_status', targetType: 'report', targetId: id, meta: { status: dto.status } } });
    return r;
  }

  /** Aggregated risk signals per user. Signals are probabilistic; they inform review, never auto-ban. */
  @Get('risk')
  async risk() {
    const since = new Date(Date.now() - 7 * 86_400_000);
    const rows = await this.prisma.riskSignal.groupBy({ by: ['userId', 'kind'], where: { createdAt: { gte: since } }, _sum: { weight: true }, _count: true });
    return rows.map((r) => ({ userId: r.userId, kind: r.kind, count: r._count, weight: r._sum.weight }));
  }

  @Post('landmarks')
  async landmark(@CurrentUser() u: AuthUser, @Body() dto: LandmarkDto) {
    const { withTerritory, ...data } = dto;
    const lm = await this.prisma.landmark.create({ data });
    if (withTerritory) await this.prisma.territory.create({ data: { landmarkId: lm.id, name: lm.name } });
    await this.prisma.auditEvent.create({ data: { actorId: u.id, action: 'admin.landmark_create', targetType: 'landmark', targetId: lm.id } });
    return lm;
  }
}
