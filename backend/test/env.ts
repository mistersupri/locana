// Loaded before each e2e file: isolated DB, no Redis (in-process bus and limiter), fixed secret.
process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL ?? 'postgresql://locana:locana@localhost:5432/locana_test?schema=public';
process.env.REDIS_URL = '';
process.env.JWT_ACCESS_SECRET = 'e2e-secret-that-is-long-enough-for-tests-0123456789';
process.env.SMTP_URL = '';
