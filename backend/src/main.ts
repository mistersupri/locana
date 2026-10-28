import { Logger, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { NestExpressApplication } from '@nestjs/platform-express';
import { WsAdapter } from '@nestjs/platform-ws';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import helmet from 'helmet';
import { AppModule } from './app.module';
import { APP_ENV, AppEnv } from './config/env';

export async function createApp() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule);
  const env = app.get<AppEnv>(APP_ENV);
  app.use(helmet());
  app.set('trust proxy', 1); // behind one reverse proxy (see docs/deployment.md); makes req.ip the client
  app.useWebSocketAdapter(new WsAdapter(app));
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  app.enableCors({ origin: env.corsOrigins.length ? env.corsOrigins : false });
  app.setGlobalPrefix('v1', { exclude: ['health', 'health/ready'] });
  app.enableShutdownHooks();
  if (env.nodeEnv !== 'production') {
    const doc = SwaggerModule.createDocument(
      app,
      new DocumentBuilder().setTitle('Locana API').setVersion('1').addBearerAuth().build(),
    );
    SwaggerModule.setup('docs', app, doc);
  }
  return app;
}

async function bootstrap() {
  const app = await createApp();
  const env = app.get<AppEnv>(APP_ENV);
  await app.listen(env.port, '0.0.0.0');
  new Logger('Bootstrap').log(`Locana API listening on :${env.port} (${env.nodeEnv})`);
}

if (require.main === module) void bootstrap();
