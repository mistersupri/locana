import { Module } from '@nestjs/common';
import { GameModule } from '../game/game.module';
import { BattleController } from './battle.controller';
import { BattleService } from './battle.service';

@Module({
  imports: [GameModule],
  controllers: [BattleController],
  providers: [BattleService],
  exports: [BattleService],
})
export class BattleModule {}
