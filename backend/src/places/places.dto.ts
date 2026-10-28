import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { ArrayMaxSize, IsArray, IsBoolean, IsIn, IsInt, IsLatitude, IsLongitude, IsOptional, IsString, IsUUID, Length, Max, Min } from 'class-validator';

const trim = ({ value }: { value: unknown }) => (typeof value === 'string' ? value.trim() : value);

export class PlaceDto {
  @ApiProperty({ example: 'Home' })
  @Transform(trim)
  @IsString()
  @Length(1, 40)
  name: string;

  @ApiProperty({ enum: ['HOME', 'WORK', 'SCHOOL', 'CUSTOM'] })
  @IsIn(['HOME', 'WORK', 'SCHOOL', 'CUSTOM'])
  type: 'HOME' | 'WORK' | 'SCHOOL' | 'CUSTOM';

  @ApiProperty() @IsLatitude() latitude: number;
  @ApiProperty() @IsLongitude() longitude: number;

  @ApiProperty({ minimum: 75, maximum: 2000, description: 'Geofence radius. Below ~75 m phone GPS is too noisy for reliable alerts.' })
  @IsInt()
  @Min(75)
  @Max(2000)
  radiusM: number;

  @ApiPropertyOptional({ default: true }) @IsOptional() @IsBoolean() notifyArrive?: boolean;
  @ApiPropertyOptional({ default: true }) @IsOptional() @IsBoolean() notifyLeave?: boolean;

  @ApiPropertyOptional({ description: 'Members who get alerts; empty means everyone in the circle who can see the person' })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(50)
  @IsUUID('4', { each: true })
  notifyUserIds?: string[];
}

export class UpdatePlaceDto {
  @ApiPropertyOptional() @IsOptional() @Transform(trim) @IsString() @Length(1, 40) name?: string;
  @ApiPropertyOptional() @IsOptional() @IsIn(['HOME', 'WORK', 'SCHOOL', 'CUSTOM']) type?: 'HOME' | 'WORK' | 'SCHOOL' | 'CUSTOM';
  @ApiPropertyOptional() @IsOptional() @IsLatitude() latitude?: number;
  @ApiPropertyOptional() @IsOptional() @IsLongitude() longitude?: number;
  @ApiPropertyOptional() @IsOptional() @IsInt() @Min(75) @Max(2000) radiusM?: number;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() notifyArrive?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() notifyLeave?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsArray() @ArrayMaxSize(50) @IsUUID('4', { each: true }) notifyUserIds?: string[];
}
