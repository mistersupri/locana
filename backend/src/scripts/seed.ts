/** Runs the game configuration by hand: npm run seed (dev) or node dist/scripts/seed.js (production image). */
import { PrismaClient } from '@prisma/client';
import { seedGameConfig } from '../game/game-config';

const prisma = new PrismaClient();

seedGameConfig(prisma)
  .then(() => console.log('seed complete'))
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
