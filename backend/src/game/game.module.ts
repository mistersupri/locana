import { Module } from '@nestjs/common';
import { CirclesModule } from '../circles/circles.module';
import { AchievementsService } from './achievements.service';
import { ChallengesService } from './challenges.service';
import { EffectsPublisher } from './effects-publisher.service';
import { ExplorationService } from './exploration.service';
import { GameController } from './game.controller';
import { LeaderboardService } from './leaderboard.service';
import { LevelService } from './level.service';
import { QuestsService } from './quests.service';
import { TerritoryService } from './territory.service';
import { XpService } from './xp.service';

@Module({
  imports: [CirclesModule],
  controllers: [GameController],
  providers: [LevelService, XpService, AchievementsService, ChallengesService, EffectsPublisher, QuestsService, TerritoryService, ExplorationService, LeaderboardService],
  exports: [LevelService, ExplorationService, QuestsService],
})
export class GameModule {}
