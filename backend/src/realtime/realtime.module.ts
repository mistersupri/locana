import { Module } from '@nestjs/common';
import { CirclesModule } from '../circles/circles.module';
import { RealtimeGateway } from './realtime.gateway';

@Module({ imports: [CirclesModule], providers: [RealtimeGateway], exports: [RealtimeGateway] })
export class RealtimeModule {}
