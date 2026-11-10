import { Module } from '@nestjs/common';
import { BattleModule } from '../battle/battle.module';
import { CirclesModule } from '../circles/circles.module';
import { GameModule } from '../game/game.module';
import { PlacesModule } from '../places/places.module';
import { SafetyModule } from '../safety/safety.module';
import { LocationController } from './location.controller';
import { LocationService } from './location.service';
import { SharingService } from './sharing.service';

@Module({
  imports: [CirclesModule, PlacesModule, GameModule, SafetyModule, BattleModule],
  controllers: [LocationController],
  providers: [LocationService, SharingService],
  exports: [SharingService, LocationService],
})
export class LocationModule {}
