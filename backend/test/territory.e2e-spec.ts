import { INestApplication } from '@nestjs/common';
import { PrismaService } from '../src/prisma/prisma.service';
import { api, register, sample, startApp, TestUser } from './helpers';

const ISTIQLAL = { lat: -6.170166, lon: 106.831375 };

/** Many members visiting the same territory at once must produce one point each and one claim. */
describe('territory concurrency', () => {
  let app: INestApplication;
  let prisma: PrismaService;

  beforeAll(async () => {
    ({ app } = await startApp());
    prisma = app.get(PrismaService);
  });
  afterAll(() => app.close());

  it('counts parallel visits exactly once and changes owner once', async () => {
    const owner = await register(app, 'Owner');
    const circle = await api(app, owner).post('/circles', { name: 'Runners' }).expect(201);
    const members: TestUser[] = [owner];
    for (let i = 0; i < 5; i++) {
      const u = await register(app, `Runner${i}`);
      const inv = await api(app, owner).post(`/circles/${circle.body.id}/invitations`, { maxUses: 1 }).expect(201);
      await api(app, u).post('/circles/join', { code: inv.body.code }).expect(200);
      members.push(u);
    }
    for (const m of members) {
      await api(app, m).patch('/me/privacy', { explorationEnabled: true }).expect(200);
      await api(app, m).patch('/me', { teamCircleId: circle.body.id }).expect(200);
    }

    const results = await Promise.all(members.map((m) => api(app, m).post('/location', { samples: [sample(ISTIQLAL.lat, ISTIQLAL.lon, 1)] })));
    for (const r of results) expect(r.status).toBe(200);

    const lm = await prisma.landmark.findUniqueOrThrow({ where: { code: 'istiqlal' }, include: { territory: true } });
    const t = await api(app, owner).get(`/territories/${lm.territory!.id}`).expect(200);
    expect(t.body.leaderboard[0]).toMatchObject({ circleId: circle.body.id, score: members.length });
    expect(t.body.ownerCircleId).toBe(circle.body.id);
    const claims = await prisma.territoryEvent.count({ where: { territoryId: lm.territory!.id, type: 'CLAIM' } });
    expect(claims).toBe(1);

    // Every member gets the landmark XP once.
    for (const m of members) {
      expect(await prisma.xPTransaction.count({ where: { userId: m.id, source: 'LANDMARK', sourceRef: lm.id } })).toBe(1);
    }
  });
});
