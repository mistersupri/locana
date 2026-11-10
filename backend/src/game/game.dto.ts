import { ApiProperty } from '@nestjs/swagger';
import { IsNumber, Max, Min } from 'class-validator';

/** Where the player is when they press Claim on a landmark. */
export class ClaimLandmarkDto {
  @ApiProperty({ example: -6.175392 }) @IsNumber() @Min(-90) @Max(90) latitude: number;
  @ApiProperty({ example: 106.827153 }) @IsNumber() @Min(-180) @Max(180) longitude: number;
  @ApiProperty({ example: 12, description: 'Horizontal accuracy in metres; claims need 40 m or better.' }) @IsNumber() @Min(0) @Max(100_000) accuracyM: number;
}
