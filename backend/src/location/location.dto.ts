import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsBoolean,
  IsDateString,
  IsIn,
  IsInt,
  IsLatitude,
  IsLongitude,
  IsNumber,
  IsOptional,
  IsUUID,
  Max,
  Min,
  ValidateNested,
} from 'class-validator';

export class LocationSampleDto {
  @ApiProperty({ example: -6.1754 })
  @IsLatitude()
  latitude: number;

  @ApiProperty({ example: 106.8272 })
  @IsLongitude()
  longitude: number;

  @ApiProperty({ description: 'Horizontal accuracy radius in metres (68% confidence)', example: 12 })
  @IsNumber()
  @Min(0.1)
  @Max(100_000)
  accuracyM: number;

  @ApiProperty({ description: 'When the fix was taken on the device (ISO-8601)' })
  @IsDateString()
  recordedAt: string;

  @ApiProperty({ description: 'Monotonic per install; used with recordedAt to drop stale updates' })
  @IsInt()
  @Min(0)
  sequence: number;

  @ApiPropertyOptional() @IsOptional() @IsNumber() @Min(0) @Max(400) speedMps?: number;
  @ApiPropertyOptional() @IsOptional() @IsNumber() @Min(0) @Max(360) bearingDeg?: number;
  @ApiPropertyOptional() @IsOptional() @IsNumber() @Min(-500) @Max(10_000) altitudeM?: number;

  @ApiProperty({ enum: ['fused', 'gps', 'network', 'passive'] })
  @IsIn(['fused', 'gps', 'network', 'passive'])
  source: string;

  @ApiPropertyOptional({ enum: ['still', 'walking', 'running', 'cycling', 'vehicle', 'unknown'] })
  @IsOptional()
  @IsIn(['still', 'walking', 'running', 'cycling', 'vehicle', 'unknown'])
  activity?: string;

  @ApiPropertyOptional({ description: 'Location.isMock() on API 31+, isFromMockProvider() before' })
  @IsOptional()
  @IsBoolean()
  isMock?: boolean;

  @ApiPropertyOptional() @IsOptional() @IsInt() @Min(0) @Max(100) batteryPct?: number;
}

export class UploadLocationsDto {
  @ApiProperty({ type: [LocationSampleDto], description: '1-100 samples, any order; queued offline samples are sent here too' })
  @ValidateNested({ each: true })
  @Type(() => LocationSampleDto)
  @ArrayMinSize(1)
  @ArrayMaxSize(100)
  samples: LocationSampleDto[];
}

export class StartSharingDto {
  @ApiProperty()
  @IsUUID()
  circleId: string;

  @ApiPropertyOptional({ description: 'Minutes until sharing stops by itself; omit to share until you stop it', example: 60 })
  @IsOptional()
  @IsInt()
  @Min(5)
  @Max(7 * 24 * 60)
  durationMinutes?: number;
}
