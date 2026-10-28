import { Module } from '@nestjs/common';
import { APP_FILTER, APP_GUARD } from '@nestjs/core';
import { ScheduleModule } from '@nestjs/schedule';
import { AdminController } from './admin/admin.controller';
import { AuthCoreModule } from './auth/auth-core.module';
import { AuthGuard } from './auth/auth.guard';
import { AuthModule } from './auth/auth.module';
import { CirclesModule } from './circles/circles.module';
import { CommonModule } from './common/common.module';
import { ApiExceptionFilter } from './common/errors';
import { RateLimitGuard } from './common/rate-limit';
import { AppConfigModule } from './config/config.module';
import { GameModule } from './game/game.module';
import { HealthController } from './health/health.controller';
import { LocationModule } from './location/location.module';
import { PlacesModule } from './places/places.module';
import { PrismaModule } from './prisma/prisma.service';
import { RealtimeModule } from './realtime/realtime.module';
import { UsersModule } from './users/users.module';

@Module({
  imports: [
    AppConfigModule,
    PrismaModule,
    CommonModule,
    AuthCoreModule,
    ScheduleModule.forRoot(),
    AuthModule,
    UsersModule,
    CirclesModule,
    LocationModule,
    PlacesModule,
    GameModule,
    RealtimeModule,
  ],
  controllers: [HealthController, AdminController],
  providers: [
    // Order matters: authenticate first so rate limits can key on the user.
    { provide: APP_GUARD, useClass: AuthGuard },
    { provide: APP_GUARD, useClass: RateLimitGuard },
    { provide: APP_FILTER, useClass: ApiExceptionFilter },
  ],
})
export class AppModule {}
