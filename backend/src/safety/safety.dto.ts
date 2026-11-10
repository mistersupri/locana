import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Transform, Type } from 'class-transformer';
import { ArrayMaxSize, IsArray, IsEmail, IsIn, IsNumber, IsOptional, IsString, Length, Matches, Max, Min, ValidateNested } from 'class-validator';

const trim = ({ value }: { value: unknown }) => (typeof value === 'string' ? value.trim() : value);

export class EmergencyContactDto {
  @ApiProperty({ example: 'Ibu' }) @Transform(trim) @IsString() @Length(1, 60) name: string;
  @ApiProperty({ example: '+628123456789' }) @Transform(trim) @IsString() @Matches(/^\+?[0-9 ()-]{6,20}$/, { message: 'phone must be a phone number' }) phone: string;
  @ApiPropertyOptional({ example: 'ibu@example.com' }) @IsOptional() @Transform(trim) @IsEmail() email?: string;
}

export class SetEmergencyContactsDto {
  @ApiProperty({ type: [EmergencyContactDto], description: 'Replaces the whole list; at most 5' })
  @IsArray() @ArrayMaxSize(5) @ValidateNested({ each: true }) @Type(() => EmergencyContactDto)
  contacts: EmergencyContactDto[];
}

export class SosDto {
  @ApiProperty({ enum: ['MANUAL', 'CRASH'] }) @IsIn(['MANUAL', 'CRASH']) reason: 'MANUAL' | 'CRASH';
  @ApiProperty() @IsNumber() @Min(-90) @Max(90) latitude: number;
  @ApiProperty() @IsNumber() @Min(-180) @Max(180) longitude: number;
  @ApiPropertyOptional() @IsOptional() @IsNumber() @Min(0) @Max(100_000) accuracyM?: number;
}

export class QuickMessageDto {
  @ApiProperty({ example: 'LOVE' }) @IsString() @Length(2, 24) code: string;
}
