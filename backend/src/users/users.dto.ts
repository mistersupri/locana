import { ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { IsBoolean, IsIn, IsInt, IsOptional, IsString, IsUUID, Length, Matches, Max, MaxLength, Min, ValidateIf } from 'class-validator';
import { USERNAME_PATTERN } from '../auth/auth.dto';

const trim = ({ value }: { value: unknown }) => (typeof value === 'string' ? value.trim() : value);
const trimLower = ({ value }: { value: unknown }) => (typeof value === 'string' ? value.trim().toLowerCase() : value);

/** Avatar styles rendered on-device from the user's initials. No photos are uploaded. */
export const AVATAR_KEYS = ['explorer_teal', 'explorer_amber', 'explorer_slate', 'explorer_coral', 'explorer_moss', 'explorer_plum'] as const;

export class UpdateProfileDto {
  @ApiPropertyOptional() @IsOptional() @Transform(trim) @IsString() @Length(1, 40) displayName?: string;
  @ApiPropertyOptional() @IsOptional() @Transform(trimLower) @Matches(USERNAME_PATTERN) username?: string;
  @ApiPropertyOptional({ enum: AVATAR_KEYS }) @IsOptional() @IsIn(AVATAR_KEYS) avatarKey?: string;
  @ApiPropertyOptional() @IsOptional() @Transform(trim) @IsString() @MaxLength(160) bio?: string;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() onboardingDone?: boolean;

  @ApiPropertyOptional({ description: 'Circle that receives your territory points; null to opt out', nullable: true })
  @IsOptional()
  @ValidateIf((_, v) => v !== null)
  @IsUUID()
  teamCircleId?: string | null;
}

export class CosmeticsDto {
  @ApiPropertyOptional({ nullable: true }) @IsOptional() @ValidateIf((_, v) => v !== null) @IsString() avatarFrameKey?: string | null;
  @ApiPropertyOptional({ nullable: true }) @IsOptional() @ValidateIf((_, v) => v !== null) @IsString() titleKey?: string | null;
  @ApiPropertyOptional({ nullable: true }) @IsOptional() @ValidateIf((_, v) => v !== null) @IsString() markerKey?: string | null;
}

export class PrivacyDto {
  @ApiPropertyOptional({ description: 'Keep a private trail of your own past locations' }) @IsOptional() @IsBoolean() historyEnabled?: boolean;
  @ApiPropertyOptional({ minimum: 1, maximum: 30 }) @IsOptional() @IsInt() @Min(1) @Max(30) historyRetentionDays?: number;
  @ApiPropertyOptional({ description: 'Let the game use your location for tiles, landmarks and quests' }) @IsOptional() @IsBoolean() explorationEnabled?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() showInLeaderboards?: boolean;

  @ApiPropertyOptional({ nullable: true })
  @IsOptional()
  @ValidateIf((_, v) => v !== null)
  @Transform(trim)
  @IsString()
  @Length(2, 24)
  leaderboardAlias?: string | null;

  @ApiPropertyOptional() @IsOptional() @IsBoolean() shareExplorationWithCircles?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() notifyArrivals?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() notifyQuests?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() notifyAchievements?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() notifyCircle?: boolean;
}

export class DeleteAccountDto {
  @ApiPropertyOptional({ description: 'Required for password accounts' })
  @IsOptional()
  @IsString()
  @MaxLength(128)
  password?: string;

  @ApiPropertyOptional({ description: 'Must be the text DELETE' })
  @IsIn(['DELETE'])
  confirm: 'DELETE';
}

export class DeviceDto {
  @IsString() @Length(8, 64) installId: string;
  @IsOptional() @IsString() @MaxLength(80) model?: string;
  @IsOptional() @IsString() @MaxLength(40) appVersion?: string;
}
