import { Body, Controller, ForbiddenException, Get, HttpCode, Inject, Param, ParseUUIDPipe, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiProperty, ApiPropertyOptional, ApiTags } from '@nestjs/swagger';
import { IsLatitude, IsLongitude, IsOptional, IsString, Length } from 'class-validator';
import { AuthUser, CurrentUser } from '../common/auth-context';
import { RateLimit } from '../common/rate-limit';
import { APP_ENV, AppEnv } from '../config/env';
import { BattleService } from './battle.service';

class AttackDto {
  @ApiPropertyOptional({ description: 'A weapon you own; leave out for the basic attack' }) @IsOptional() @IsString() @Length(2, 40) weaponCode?: string;
}
class BuyDto {
  @ApiProperty() @IsString() @Length(2, 40) code: string;
}
class DevSpawnDto {
  @ApiProperty() @IsLatitude() latitude: number;
  @ApiProperty() @IsLongitude() longitude: number;
  @ApiPropertyOptional() @IsOptional() @IsString() monster?: string;
}

const uuid = new ParseUUIDPipe({ version: '4' });

@ApiTags('battle')
@ApiBearerAuth()
@Controller()
export class BattleController {
  constructor(private readonly battle: BattleService, @Inject(APP_ENV) private readonly env: AppEnv) {}

  @Get('battle/active')
  active(@CurrentUser() u: AuthUser) {
    return this.battle.active(u.id);
  }

  @Post('battle/:id/attack')
  @HttpCode(200)
  @RateLimit({ name: 'battle-attack', limit: 120, windowSec: 60 })
  attack(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: AttackDto) {
    return this.battle.attack(u.id, id, dto.weaponCode);
  }

  @Post('battle/:id/flee')
  @HttpCode(200)
  flee(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.battle.flee(u.id, id);
  }

  @Get('shop')
  shop(@CurrentUser() u: AuthUser) {
    return this.battle.shop(u.id);
  }

  @Post('shop/buy')
  @HttpCode(200)
  buy(@CurrentUser() u: AuthUser, @Body() dto: BuyDto) {
    return this.battle.buy(u.id, dto.code);
  }

  /** Development only: meet a monster now, for trying the fight without walking around. Absent in production. */
  @Post('battle/dev-spawn')
  @HttpCode(200)
  async devSpawn(@CurrentUser() u: AuthUser, @Body() dto: DevSpawnDto) {
    if (this.env.nodeEnv === 'production') throw new ForbiddenException();
    await this.battle.spawn(u.id, { latitude: dto.latitude, longitude: dto.longitude }, new Date(), dto.monster);
    return this.battle.active(u.id);
  }
}
