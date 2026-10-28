import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsEmail, IsOptional, IsString, Length, Matches, MaxLength, MinLength } from 'class-validator';
import { Transform } from 'class-transformer';

const trimLower = ({ value }: { value: unknown }) => (typeof value === 'string' ? value.trim().toLowerCase() : value);
const trim = ({ value }: { value: unknown }) => (typeof value === 'string' ? value.trim() : value);

export const USERNAME_PATTERN = /^[a-z0-9_.]{3,24}$/;

export class RegisterDto {
  @ApiProperty({ example: 'rani@example.com' })
  @Transform(trimLower)
  @IsEmail()
  @MaxLength(254)
  email: string;

  @ApiProperty({ minLength: 10, maxLength: 128 })
  @IsString()
  @MinLength(10, { message: 'Password needs at least 10 characters' })
  @MaxLength(128)
  password: string;

  @ApiProperty({ example: 'rani.walks', description: '3-24 chars: lowercase letters, digits, underscore, dot' })
  @Transform(trimLower)
  @Matches(USERNAME_PATTERN, { message: 'Username can use 3-24 lowercase letters, digits, _ or .' })
  username: string;

  @ApiProperty({ example: 'Rani' })
  @Transform(trim)
  @IsString()
  @Length(1, 40)
  displayName: string;

  @ApiPropertyOptional({ example: 'Pixel 8' })
  @IsOptional()
  @IsString()
  @MaxLength(80)
  deviceName?: string;
}

export class LoginDto {
  @ApiProperty({ description: 'Email or username' })
  @Transform(trimLower)
  @IsString()
  @Length(3, 254)
  identifier: string;

  @ApiProperty()
  @IsString()
  @MaxLength(128)
  password: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(80)
  deviceName?: string;
}

export class RefreshDto {
  @ApiProperty()
  @IsString()
  @Length(20, 200)
  refreshToken: string;
}

export class ForgotPasswordDto {
  @ApiProperty()
  @Transform(trimLower)
  @IsEmail()
  email: string;
}

export class ResetPasswordDto {
  @ApiProperty()
  @Transform(trimLower)
  @IsEmail()
  email: string;

  @ApiProperty({ description: 'Code from the reset e-mail' })
  @IsString()
  @Length(6, 20)
  code: string;

  @ApiProperty()
  @IsString()
  @MinLength(10, { message: 'Password needs at least 10 characters' })
  @MaxLength(128)
  newPassword: string;
}

export class GoogleSignInDto {
  @ApiProperty({ description: 'ID token from Credential Manager / Google Identity Services' })
  @IsString()
  @Length(20, 4096)
  idToken: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(80)
  deviceName?: string;
}

export class ChangePasswordDto {
  @ApiProperty()
  @IsString()
  @MaxLength(128)
  currentPassword: string;

  @ApiProperty()
  @IsString()
  @MinLength(10, { message: 'Password needs at least 10 characters' })
  @MaxLength(128)
  newPassword: string;
}
