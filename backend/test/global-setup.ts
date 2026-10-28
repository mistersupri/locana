import { execSync } from 'child_process';

/** Rebuilds the e2e database from migrations and seeds game config. Never points at the dev DB. */
export default async function globalSetup() {
  const url = process.env.TEST_DATABASE_URL ?? 'postgresql://locana:locana@localhost:5432/locana_test?schema=public';
  if (!/test/.test(url)) throw new Error('TEST_DATABASE_URL must name a test database');
  const env = { ...process.env, DATABASE_URL: url };
  execSync('npx prisma migrate reset --force --skip-seed --skip-generate', { env, stdio: 'pipe' });
  execSync('npx ts-node src/scripts/seed.ts', { env, stdio: 'pipe' });
}
