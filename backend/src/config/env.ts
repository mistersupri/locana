/** Typed, validated environment. Fails fast at boot when required values are missing. */
export interface AppEnv {
  nodeEnv: string;
  port: number;
  databaseUrl: string;
  redisUrl?: string;
  jwtAccessSecret: string;
  accessTokenTtlSeconds: number;
  refreshTokenTtlDays: number;
  googleClientId?: string;
  smtpUrl?: string;
  mailFrom: string;
  locationHistoryRetentionDays: number;
  corsOrigins: string[];
  adminUserIds: string[];
}

function required(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`Missing required environment variable ${name}`);
  return v;
}

export function loadEnv(): AppEnv {
  const nodeEnv = process.env.NODE_ENV ?? 'development';
  const jwtAccessSecret = required('JWT_ACCESS_SECRET');
  if (nodeEnv === 'production' && jwtAccessSecret.length < 32) {
    throw new Error('JWT_ACCESS_SECRET must be at least 32 characters in production');
  }
  const list = (v?: string) => (v ?? '').split(',').map((s) => s.trim()).filter(Boolean);
  return {
    nodeEnv,
    port: Number(process.env.PORT ?? 3000),
    databaseUrl: required('DATABASE_URL'),
    redisUrl: process.env.REDIS_URL || undefined,
    jwtAccessSecret,
    accessTokenTtlSeconds: Number(process.env.ACCESS_TOKEN_TTL_SECONDS ?? 900),
    refreshTokenTtlDays: Number(process.env.REFRESH_TOKEN_TTL_DAYS ?? 30),
    googleClientId: process.env.GOOGLE_CLIENT_ID || undefined,
    smtpUrl: process.env.SMTP_URL || undefined,
    mailFrom: process.env.MAIL_FROM ?? 'Locana <no-reply@example.com>',
    locationHistoryRetentionDays: Number(process.env.LOCATION_HISTORY_RETENTION_DAYS ?? 30),
    corsOrigins: list(process.env.CORS_ORIGINS),
    adminUserIds: list(process.env.ADMIN_USER_IDS),
  };
}

export const APP_ENV = Symbol('APP_ENV');
