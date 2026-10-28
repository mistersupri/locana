import { Module } from '@nestjs/common';
import { CircleAccessService } from './circle-access.service';
import { CirclesController } from './circles.controller';
import { CirclesService } from './circles.service';
import { FeedService } from './feed.service';

@Module({
  controllers: [CirclesController],
  providers: [CircleAccessService, CirclesService, FeedService],
  exports: [CircleAccessService, CirclesService, FeedService],
})
export class CirclesModule {}
