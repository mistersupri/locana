import { INestApplication } from '@nestjs/common';
import { PrismaService } from '../src/prisma/prisma.service';
import { api, register, sample, startApp } from './helpers';

/** A person's own trail must start with their first uploads, without anything having read their sharing state first. */
describe('own history', () => {
  let app: INestApplication;
  let prisma: PrismaService;

  beforeAll(async () => {
    ({ app } = await startApp());
    prisma = app.get(PrismaService);
  });
  afterAll(() => app.close());

  it('keeps a trail and shares from the first upload after creating a circle', async () => {
    const u = await register(app, 'Walker');
    const circle = await api(app, u).post('/circles', { name: 'Personal' }).expect(201);
    const r = await api(app, u)
      .post('/location', { samples: [sample(-6.2, 106.8, 200, { sequence: 1 }), sample(-6.1995, 106.8, 120, { sequence: 2 }), sample(-6.199, 106.8, 40, { sequence: 3 })] })
      .expect(200);
    expect(r.body.sharingActive).toBe(true);
    expect(await prisma.locationHistory.count({ where: { userId: u.id } })).toBeGreaterThanOrEqual(2);
    const day = new Date().toISOString().slice(0, 10);
    const t = await api(app, u).get(`/circles/${circle.body.id}/members/${u.id}/timeline?day=${day}&tzOffsetMinutes=0`).expect(200);
    expect(t.body.points).toBeGreaterThanOrEqual(2);
  });

  it('keeps the trail even when sharing was stopped on purpose, without sharing the position', async () => {
    const u = await register(app, 'Returner');
    const circle = await api(app, u).post('/circles', { name: 'Home' }).expect(201);
    await api(app, u).post('/location', { samples: [sample(-6.2, 106.8, 300, { sequence: 1 })] }).expect(200);
    await prisma.locationSharingSession.updateMany({ where: { userId: u.id, circleId: circle.body.id }, data: { status: 'ENDED', endReason: 'STOPPED', endedAt: new Date() } });
    const r = await api(app, u)
      .post('/location', { samples: [sample(-6.2, 106.8, 100, { sequence: 2 }), sample(-6.1995, 106.8, 20, { sequence: 3 })] })
      .expect(200);
    expect(r.body.sharingActive).toBe(false);
    expect(await prisma.locationHistory.count({ where: { userId: u.id } })).toBeGreaterThanOrEqual(1);
  });
});
