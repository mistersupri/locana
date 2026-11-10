import { Module } from '@nestjs/common';
import { CirclesModule } from '../circles/circles.module';
import { MailModule } from '../common/mail.module';
import { SafetyController } from './safety.controller';
import { SafetyService } from './safety.service';

@Module({
  imports: [CirclesModule, MailModule],
  controllers: [SafetyController],
  providers: [SafetyService],
  exports: [SafetyService],
})
export class SafetyModule {}
