import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { IsBoolean, IsIn, IsInt, IsOptional, IsString, IsUUID, Length, Max, MaxLength, Min } from 'class-validator';

const trim = ({ value }: { value: unknown }) => (typeof value === 'string' ? value.trim() : value);

export const CIRCLE_AVATARS = ['circle_compass', 'circle_house', 'circle_heart', 'circle_trail', 'circle_flag', 'circle_star'] as const;

export class CreateCircleDto {
  @ApiProperty({ example: 'Weekend crew' })
  @Transform(trim)
  @IsString()
  @Length(1, 40)
  name: string;

  @ApiPropertyOptional({ enum: CIRCLE_AVATARS })
  @IsOptional()
  @IsIn(CIRCLE_AVATARS)
  avatarKey?: string;
}

export class UpdateCircleDto {
  @ApiPropertyOptional()
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 40)
  name?: string;

  @ApiPropertyOptional({ enum: CIRCLE_AVATARS })
  @IsOptional()
  @IsIn(CIRCLE_AVATARS)
  avatarKey?: string;
}

export class CreateInvitationDto {
  @ApiPropertyOptional({ description: 'Hours until the code stops working (1-168)', default: 48 })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(168)
  expiresInHours?: number;

  @ApiPropertyOptional({ description: 'How many people can join with it (1-50)', default: 5 })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(50)
  maxUses?: number;
}

export class JoinCircleDto {
  @ApiProperty({ example: 'K7QH-M3XP-2R' })
  @IsString()
  @Length(6, 20)
  code: string;
}

export class SetRoleDto {
  @ApiProperty({ enum: ['ADMIN', 'MEMBER', 'OWNER'] })
  @IsIn(['ADMIN', 'MEMBER', 'OWNER'])
  role: 'ADMIN' | 'MEMBER' | 'OWNER';
}

export class MemberPrefsDto {
  @ApiPropertyOptional({ description: 'Mute circle notifications for yourself' })
  @IsOptional()
  @IsBoolean()
  muted?: boolean;
}

export class VisibilityDto {
  @ApiProperty({ description: 'Member who should no longer see your location in this circle' })
  @IsUUID()
  viewerId: string;

  @ApiProperty()
  @IsBoolean()
  hidden: boolean;
}

export class ReportDto {
  @ApiProperty({ enum: ['HARASSMENT', 'UNWANTED_TRACKING', 'SPAM', 'IMPERSONATION', 'OTHER'] })
  @IsIn(['HARASSMENT', 'UNWANTED_TRACKING', 'SPAM', 'IMPERSONATION', 'OTHER'])
  reason: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(1000)
  details?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsUUID()
  circleId?: string;
}

export class ChallengeDto {
  @ApiProperty({ example: 'Map 30 new tiles together' })
  @Transform(trim)
  @IsString()
  @Length(3, 60)
  title: string;

  @ApiProperty({ enum: ['TILES', 'LANDMARKS', 'QUESTS'] })
  @IsIn(['TILES', 'LANDMARKS', 'QUESTS'])
  metric: 'TILES' | 'LANDMARKS' | 'QUESTS';

  @ApiProperty({ minimum: 1, maximum: 500 })
  @IsInt()
  @Min(1)
  @Max(500)
  target: number;

  @ApiProperty({ minimum: 1, maximum: 30, description: 'Duration in days' })
  @IsInt()
  @Min(1)
  @Max(30)
  days: number;
}
