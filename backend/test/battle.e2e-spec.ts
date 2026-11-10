import { INestApplication } from '@nestjs/common';
import { PrismaService } from '../src/prisma/prisma.service';
import { api, register, sample, startApp, TestUser } from './helpers';

describe('monsters, weapons and the shop', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let a: TestUser;

  beforeAll(async () => {
    ({ app } = await startApp());
    prisma = app.get(PrismaService);
    a = await register(app, 'Hero');
  });
  afterAll(() => app.close());

  const spawn = async (monster = 'slime') => (await api(app, a).post('/battle/dev-spawn', { latitude: -6.2, longitude: 106.8, monster }).expect(200)).body;

  it('a monster can be fought with the basic attack and pays coins and XP once', async () => {
    const before = await api(app, a).get('/battle/active').expect(200);
    expect(before.body.encounter).toBeNull();
    expect(before.body.weapons.map((w: { code: string }) => w.code)).toEqual(['fists']);
    const s = await spawn('slime');
    expect(s.encounter).toMatchObject({ status: 'ACTIVE', hp: 30, maxHp: 30, monster: { code: 'slime' } });
    const id = s.encounter.id as string;
    let last: { outcome: string; coins: number; rewards: { coins: number; xp: number } | null } | undefined;
    for (let i = 0; i < 12 && last?.outcome !== 'WON'; i++) {
      last = (await api(app, a).post(`/battle/${id}/attack`, {}).expect(200)).body;
      if (last?.outcome === 'LOST') throw new Error('the slime should not beat a fresh player');
    }
    expect(last?.outcome).toBe('WON');
    expect(last?.rewards).toMatchObject({ coins: 10, xp: 15 });
    expect(last?.coins).toBe(10);
    await api(app, a).post(`/battle/${id}/attack`, {}).expect(409); // already over
    const me = await prisma.userProfile.findUniqueOrThrow({ where: { userId: a.id } });
    expect(me.coins).toBe(10);
    expect(await prisma.xPTransaction.count({ where: { userId: a.id, source: 'MONSTER' } })).toBe(1);
    const ach = await api(app, a).get('/achievements').expect(200);
    expect(JSON.stringify(ach.body)).toContain('First Blood');
  });

  it('weapons are bought with coins, need a level, and cannot be used before owning them', async () => {
    const shop = await api(app, a).get('/shop').expect(200);
    expect(shop.body.weapons.find((w: { code: string }) => w.code === 'wooden_sword')).toMatchObject({ owned: false, affordable: false, unlocked: true });
    await api(app, a).post('/shop/buy', { code: 'wooden_sword' }).expect(409); // 10 coins is not 40
    await api(app, a).post('/shop/buy', { code: 'nope' }).expect(400);
    await prisma.userProfile.update({ where: { userId: a.id }, data: { coins: 100 } });
    await api(app, a).post('/shop/buy', { code: 'dragon_claw' }).expect(409); // level 13
    const bought = await api(app, a).post('/shop/buy', { code: 'wooden_sword' }).expect(200);
    expect(bought.body.coins).toBe(60);
    await api(app, a).post('/shop/buy', { code: 'wooden_sword' }).expect(409); // already owned
    const s = await spawn('bat');
    const id = s.encounter.id as string;
    await api(app, a).post(`/battle/${id}/attack`, { weaponCode: 'iron_blade' }).expect(403);
    const hit = await api(app, a).post(`/battle/${id}/attack`, { weaponCode: 'wooden_sword' }).expect(200);
    expect(hit.body.hit.damage).toBeGreaterThanOrEqual(11); // 14 power, 15% spread
    await api(app, a).post(`/battle/${id}/flee`).expect(200);
    await api(app, a).post(`/battle/${id}/flee`).expect(409);
    expect((await api(app, a).get('/battle/active').expect(200)).body.encounter).toBeNull();
  });

  it('a monster only appears after travelling, never twice at once, and respects the cooldown', async () => {
    const { BattleService } = await import('../src/battle/battle.service');
    const svc = app.get(BattleService);
    const u = await register(app, 'Walker');
    const here = { latitude: -6.3, longitude: 106.9, accuracyM: 10 };
    // The service rolls with Math.random: make it always lucky for this test.
    const lucky = jest.spyOn(Math, 'random').mockReturnValue(0.01);
    try {
      const first = await svc.maybeSpawn(u.id, here);
      expect(first).not.toBeNull();
      expect(await svc.maybeSpawn(u.id, { ...here, latitude: -6.31 })).toBeNull(); // one at a time
      await prisma.encounter.updateMany({ where: { userId: u.id }, data: { status: 'FLED', createdAt: new Date(Date.now() - 9 * 60_000) } });
      expect(await svc.maybeSpawn(u.id, { ...here, latitude: -6.3001 })).toBeNull(); // has not travelled
      expect(await svc.maybeSpawn(u.id, { ...here, latitude: -6.305 })).not.toBeNull(); // 550 m on
    } finally {
      lucky.mockRestore();
    }
  });

  it('quick messages you have earned join the chips; others are refused', async () => {
    const b = await register(app, 'Friend');
    const circle = await api(app, a).post('/circles', { name: 'Pals' }).expect(201);
    const inv = await api(app, a).post(`/circles/${circle.body.id}/invitations`, { maxUses: 1 }).expect(201);
    await api(app, b).post('/circles/join', { code: inv.body.code }).expect(200);
    const send = (code: string) => api(app, a).post(`/circles/${circle.body.id}/members/${b.id}/quick-message`, { code });
    expect((await api(app, a).get('/quick-messages').expect(200)).body.map((m: { code: string }) => m.code)).not.toContain('GOAT');
    await send('GOAT').expect(403);
    const def = await prisma.collectibleDefinition.findUniqueOrThrow({ where: { code: 'qm_goat' } });
    await prisma.userCollectible.create({ data: { userId: a.id, collectibleId: def.id, source: 'test' } });
    expect((await api(app, a).get('/quick-messages').expect(200)).body.map((m: { code: string }) => m.code)).toContain('GOAT');
    await send('GOAT').expect(200);
  });

  it('driving events from the phone are kept and counted on the trip with the route times', async () => {
    const d = await register(app, 'Driver');
    // 20 one-minute steps along a road, 80 m each, ending now. Two hard brakes and one phone use.
    const samples = Array.from({ length: 21 }, (_, i) => {
      const extra: Record<string, unknown> = { speedMps: 10, activity: 'vehicle', sequence: 5000 + i };
      if (i === 5 || i === 12) extra.driveEvent = 'HARD_BRAKE';
      if (i === 15) extra.driveEvent = 'PHONE_USE';
      return sample(-6.2 - i * 0.0008, 106.8, (20 - i) * 60, extra);
    });
    await api(app, d).post('/location', { samples }).expect(200);
    expect(await prisma.driveEvent.count({ where: { userId: d.id } })).toBe(3);
    const circle = await api(app, d).post('/circles', { name: 'Solo' }).expect(201);
    const day = new Date().toISOString().slice(0, 10);
    const t = (await api(app, d).get(`/circles/${circle.body.id}/members/${d.id}/timeline?day=${day}&tzOffsetMinutes=0`).expect(200)).body;
    const trip = t.segments.find((s: { type: string }) => s.type === 'TRIP');
    expect(trip.eventCounts).toEqual({ hardBrake: 2, rapidAccel: 0, phoneUse: 1 });
    expect(trip.pathAt).toHaveLength(trip.path.length);
    expect(trip.pathAt[0]).toBeLessThan(trip.pathAt[trip.pathAt.length - 1]);
  });
});
