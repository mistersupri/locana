import { Module } from '@nestjs/common';
import { CirclesModule } from '../circles/circles.module';
import { GameModule } from '../game/game.module';
import { LocationModule } from '../location/location.module';
import { UsersController } from './users.controller';
import { UsersService } from './users.service';

@Module({
  imports: [CirclesModule, GameModule, LocationModule],
  controllers: [UsersController],
  providers: [UsersService],
  exports: [UsersService],
})
export class UsersModule {}
