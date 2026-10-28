import { Global, Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { APP_ENV, AppEnv } from '../config/env';
import { TokenService } from './token.service';

/** Token issuing and verification, needed by the HTTP guard, the WebSocket gateway and account deletion. */
@Global()
@Module({
  imports: [
    JwtModule.registerAsync({
      inject: [APP_ENV],
      useFactory: (env: AppEnv) => ({ secret: env.jwtAccessSecret, signOptions: { algorithm: 'HS256', issuer: 'locana' }, verifyOptions: { algorithms: ['HS256'], issuer: 'locana' } }),
    }),
  ],
  providers: [TokenService],
  exports: [TokenService],
})
export class AuthCoreModule {}
