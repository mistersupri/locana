import { INestApplication } from '@nestjs/common';
import { PrismaService } from '../src/prisma/prisma.service';
import { api, register, sample, startApp, TestUser } from './helpers';

describe('safety: emergency contacts, SOS, quick messages, long trips', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let a: TestUser;
  let b: TestUser;
  let outsider: TestUser;
  let circleId: string;

  const notificationsOf = async (u: TestUser) => (await api(app, u).get('/notifications').expect(200)).body.items as { kind: string; title: string; data: Record<string, unknown> }[];

  beforeAll(async () => {
    ({ app } = await startApp());
    prisma = app.get(PrismaService);
    a = await register(app, 'Ana');
    b = await register(app, 'Budi');
    outsider = await register(app, 'Outsider');
    const circle = await api(app, a).post('/circles', { name: 'Family' }).expect(201);
    circleId = circle.body.id;
    const inv = await api(app, a).post(`/circles/${circleId}/invitations`, { maxUses: 1 }).expect(201);
    await api(app, b).post('/circles/join', { code: inv.body.code }).expect(200);
  });
  afterAll(() => app.close());

  it('keeps a private list of at most five emergency contacts', async () => {
    const set = await api(app, a)
      .put('/emergency-contacts', { contacts: [{ name: 'Ibu', phone: '+62 812 3456 789', email: 'ibu@example.test' }, { name: 'Bapak', phone: '0811223344' }] })
      .expect(200);
    expect(set.body).toHaveLength(2);
    expect((await api(app, a).get('/emergency-contacts').expect(200)).body.map((c: { name: string }) => c.name)).toEqual(['Ibu', 'Bapak']);
    expect((await api(app, b).get('/emergency-contacts').expect(200)).body).toEqual([]);
    await api(app, a).put('/emergency-contacts', { contacts: [{ name: 'X', phone: 'not a phone' }] }).expect(400);
    const six = Array.from({ length: 6 }, (_, i) => ({ name: `C${i}`, phone: '0811223344' }));
    await api(app, a).put('/emergency-contacts', { contacts: six }).expect(400);
  });

  it('SOS tells circle members with the position, e-mails, and returns the phone numbers to call', async () => {
    const r = await api(app, a).post('/sos', { reason: 'MANUAL', latitude: -6.2, longitude: 106.8, accuracyM: 12 }).expect(200);
    expect(r.body.notified).toBe(1);
    expect(r.body.emailed).toBeGreaterThanOrEqual(2); // Budi and Ibu
    expect(r.body.contacts).toEqual([{ name: 'Ibu', phone: '+62 812 3456 789' }, { name: 'Bapak', phone: '0811223344' }]);
    const n = (await notificationsOf(b)).find((x) => x.kind === 'SOS');
    expect(n?.title).toContain('needs help');
    expect(n?.data.mapsUrl).toContain('-6.2,106.8');
    expect((await notificationsOf(outsider)).some((x) => x.kind === 'SOS')).toBe(false);
    expect(await prisma.sosEvent.count({ where: { userId: a.id } })).toBe(1);
    await api(app, a).post('/sos', { reason: 'NOPE', latitude: 0, longitude: 0 }).expect(400);
  });

  it('quick messages: a fixed list, circle members only, rate limited, delivered as a notification', async () => {
    const list = await api(app, a).get('/quick-messages').expect(200);
    expect(list.body.map((m: { code: string }) => m.code)).toContain('LOVE');
    const sent = await api(app, a).post(`/circles/${circleId}/members/${b.id}/quick-message`, { code: 'LOVE' }).expect(200);
    expect(sent.body).toMatchObject({ sent: true, delivered: true, emoji: '❤️' });
    const n = (await notificationsOf(b)).find((x) => x.kind === 'QUICK_MESSAGE');
    expect(n?.title).toContain('Love ya!');
    expect(n?.data).toMatchObject({ code: 'LOVE', fromUserId: a.id });
    await api(app, a).post(`/circles/${circleId}/members/${b.id}/quick-message`, { code: 'HAHA' }).expect(429);
    await api(app, a).post(`/circles/${circleId}/members/${a.id}/quick-message`, { code: 'LOVE' }).expect(400);
    await api(app, a).post(`/circles/${circleId}/members/${b.id}/quick-message`, { code: 'FREE TEXT' }).expect(400);
    await api(app, outsider).post(`/circles/${circleId}/members/${b.id}/quick-message`, { code: 'LOVE' }).expect(404);
  });

  it('a long trip tells the circle once, with a distance and no coordinates', async () => {
    // 25 one-minute steps of about 650 m: roughly 16 km, ending now.
    const samples = Array.from({ length: 26 }, (_, i) =>
      sample(-6.1 - i * 0.0059, 106.8, (25 - i) * 60, { speedMps: 11, activity: 'vehicle', sequence: 1000 + i }),
    );
    await api(app, a).post('/location', { samples }).expect(200);
    const trips = (await notificationsOf(b)).filter((x) => x.kind === 'TRIP');
    expect(trips).toHaveLength(1);
    expect(trips[0].title).toContain('long trip');
    expect(JSON.stringify(trips[0])).not.toMatch(/latitude|longitude/);
    await api(app, a).post('/location', { samples: [sample(-6.1 - 26 * 0.0059, 106.8, 5, { speedMps: 11, activity: 'vehicle', sequence: 2000 })] }).expect(200);
    expect((await notificationsOf(b)).filter((x) => x.kind === 'TRIP')).toHaveLength(1);
  });
});
