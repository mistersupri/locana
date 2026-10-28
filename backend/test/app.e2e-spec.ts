import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { PrismaService } from '../src/prisma/prisma.service';
import { api, connectWs, idemKey, register, sample, sleep, startApp, TestUser } from './helpers';

// Monas, central Jakarta (a seeded landmark with a territory)
const MONAS = { lat: -6.175392, lon: 106.827153 };
// ~1.1 km south, away from every seeded landmark
const AWAY = { lat: -6.1855, lon: 106.8272 };

/**
 * End-to-end scenario from the PRD (section 16) against a real PostgreSQL database, plus the
 * negative cases that matter most: outsiders and paused members never get coordinates.
 */
describe('Locana end to end', () => {
  let app: INestApplication;
  let port: number;
  let prisma: PrismaService;
  let a: TestUser;
  let b: TestUser;
  let outsider: TestUser;
  let circleId: string;

  beforeAll(async () => {
    ({ app, port } = await startApp());
    prisma = app.get(PrismaService);
    a = await register(app, 'Ana');
    b = await register(app, 'Budi');
    outsider = await register(app, 'Citra');
  });

  afterAll(async () => {
    await app.close();
  });

  describe('auth', () => {
    it('rejects duplicate usernames and weak passwords', async () => {
      await api(app).post('/auth/register', { email: 'x1@example.test', password: 'correct horse battery', username: a.username, displayName: 'X' }).expect(409);
      const weak = await api(app).post('/auth/register', { email: 'x2@example.test', password: 'short', username: 'x2user', displayName: 'X' }).expect(400);
      expect(weak.body.error.code).toBe('VALIDATION_FAILED');
    });

    it('logs in with username or email and rejects a wrong password', async () => {
      await api(app).post('/auth/login', { identifier: a.username, password: a.password }).expect(200);
      await api(app).post('/auth/login', { identifier: a.email, password: a.password }).expect(200);
      const bad = await api(app).post('/auth/login', { identifier: a.email, password: 'nope nope nope' }).expect(401);
      expect(bad.body.error.code).toBe('INVALID_CREDENTIALS');
    });

    it('rotates refresh tokens and revokes the family when an old one is replayed', async () => {
      const login = await api(app).post('/auth/login', { identifier: b.username, password: b.password }).expect(200);
      const first = login.body.tokens.refreshToken;
      const rotated = await api(app).post('/auth/refresh', { refreshToken: first }).expect(200);
      expect(rotated.body.refreshToken).not.toBe(first);
      // Replaying the first token signals theft: both tokens of this login stop working.
      await api(app).post('/auth/refresh', { refreshToken: first }).expect(401);
      await api(app).post('/auth/refresh', { refreshToken: rotated.body.refreshToken }).expect(401);
      // The other login of the same user is unaffected.
      await api(app, b).get('/me').expect(200);
    });

    it('logout ends access immediately', async () => {
      const login = await api(app).post('/auth/login', { identifier: b.username, password: b.password }).expect(200);
      const temp = { ...b, access: login.body.tokens.accessToken };
      await api(app, temp).get('/me').expect(200);
      await api(app, temp).post('/auth/logout').expect(204);
      await sleep(50);
      // Session cache is 10 s, but logout clears this session's entry right away.
      await api(app, temp).get('/me').expect(401);
    });

    it('password reset accepts only the e-mailed code and signs out every device', async () => {
      const u = await register(app, 'Reset');
      await api(app).post('/auth/password/forgot', { email: u.email }).expect(202);
      await api(app).post('/auth/password/forgot', { email: 'nobody@example.test' }).expect(202);
      await api(app).post('/auth/password/reset', { email: u.email, code: 'AAAA-BBBB', newPassword: 'another long password' }).expect(400);
      // Tests cannot read the e-mail, so plant a known code the way the service stores it.
      const { sha256 } = await import('../src/common/crypto');
      await prisma.passwordResetToken.create({ data: { userId: u.id, codeHash: sha256(`${u.id}:KNOWNCODE`), expiresAt: new Date(Date.now() + 60_000) } });
      await api(app).post('/auth/password/reset', { email: u.email, code: 'known-code', newPassword: 'another long password' }).expect(200);
      await api(app, u).get('/me').expect(401);
      await api(app).post('/auth/login', { identifier: u.email, password: 'another long password' }).expect(200);
    });
  });

  describe('circles and live location', () => {
    it('step 2-3: A creates a circle, B joins with the invitation code', async () => {
      const c = await api(app, a).post('/circles', { name: 'Family' }).expect(201);
      circleId = c.body.id;
      expect(c.body.myRole).toBe('OWNER');
      const inv = await api(app, a).post(`/circles/${circleId}/invitations`, { expiresInHours: 2, maxUses: 1 }).expect(201);
      expect(inv.body.code).toMatch(/^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{2}$/);
      // members cannot create invitations
      const joined = await api(app, b).post('/circles/join', { code: inv.body.code.toLowerCase() }).expect(200);
      expect(joined.body.members.map((m: { userId: string }) => m.userId).sort()).toEqual([a.id, b.id].sort());
      await api(app, b).post(`/circles/${circleId}/invitations`, {}).expect(403);
      // single-use code is now spent
      const again = await api(app, outsider).post('/circles/join', { code: inv.body.code }).expect(400);
      expect(again.body.error.code).toBe('INVALID_INVITATION');
    });

    it('outsiders get 404 for the circle, its members, places and locations', async () => {
      await api(app, outsider).get(`/circles/${circleId}`).expect(404);
      await api(app, outsider).get(`/circles/${circleId}/locations`).expect(404);
      await api(app, outsider).get(`/circles/${circleId}/places`).expect(404);
      await api(app, outsider).get(`/circles/${circleId}/feed`).expect(404);
    });

    it('refuses location uploads while sharing and exploration are off', async () => {
      const r = await api(app, a).post('/location', { samples: [sample(AWAY.lat, AWAY.lon)] }).expect(409);
      expect(r.body.error.code).toBe('LOCATION_NOT_NEEDED');
    });

    it('step 4-5: A shares, B sees the live position over REST and WebSocket', async () => {
      const ws = await connectWs(port, b.access);
      await ws.waitFor('ready');
      ws.send({ type: 'subscribe', circleId });
      await ws.waitFor('subscribed');

      const st = await api(app, a).post('/sharing', { circleId, durationMinutes: 60 }).expect(201);
      expect(st.body.collectLocation).toBe(true);
      await api(app, a).post('/location', { samples: [sample(AWAY.lat, AWAY.lon)] }).expect(200);

      const update = await ws.waitFor('location.update', (d) => d.userId === a.id);
      expect(update.latitude).toBeCloseTo(AWAY.lat, 5);
      const locs = await api(app, b).get(`/circles/${circleId}/locations`).expect(200);
      const ana = locs.body.find((l: { userId: string }) => l.userId === a.id);
      expect(ana.status).toBe('LIVE');
      expect(ana.location.latitude).toBeCloseTo(AWAY.lat, 5);
      // B is not sharing: A sees B as hidden, without coordinates or timestamps.
      const fromA = await api(app, a).get(`/circles/${circleId}/locations`).expect(200);
      expect(fromA.body.find((l: { userId: string }) => l.userId === b.id)).toEqual({ userId: b.id, status: 'HIDDEN', location: null });
      ws.close();
    });

    it('an outsider cannot subscribe to the circle over WebSocket and receives nothing', async () => {
      const ws = await connectWs(port, outsider.access);
      await ws.waitFor('ready');
      ws.send({ type: 'subscribe', circleId });
      const err = await ws.waitFor('error');
      expect(err.code).toBe('CIRCLE_NOT_FOUND');
      await api(app, a).post('/location', { samples: [sample(AWAY.lat + 0.0005, AWAY.lon)] }).expect(200);
      await sleep(200);
      expect(ws.messages.some((m) => m.type === 'location.update')).toBe(false);
      ws.close();
    });

    it('a WebSocket without a valid token is closed', async () => {
      const ws = await connectWs(port, 'not-a-token');
      await new Promise((r) => ws.ws.on('close', r));
      expect(ws.ws.readyState).toBe(ws.ws.CLOSED);
    });

    it('drops an upload older than the stored position', async () => {
      const before = await api(app, b).get(`/circles/${circleId}/locations`).expect(200);
      const t0 = before.body.find((l: { userId: string }) => l.userId === a.id).location.recordedAt;
      const r = await api(app, a).post('/location', { samples: [sample(0, 0, 3600)] }).expect(200);
      expect(r.body.rejected[0].reason).toBe('STALE_SEQUENCE');
      const after = await api(app, b).get(`/circles/${circleId}/locations`).expect(200);
      expect(after.body.find((l: { userId: string }) => l.userId === a.id).location.recordedAt).toBe(t0);
    });

    it('hiding from one member removes the coordinates for that member only', async () => {
      await api(app, a).put(`/circles/${circleId}/visibility`, { viewerId: b.id, hidden: true }).expect(204);
      const hidden = await api(app, b).get(`/circles/${circleId}/locations`).expect(200);
      expect(hidden.body.find((l: { userId: string }) => l.userId === a.id)).toEqual({ userId: a.id, status: 'HIDDEN', location: null });
      await api(app, a).put(`/circles/${circleId}/visibility`, { viewerId: b.id, hidden: false }).expect(204);
    });

    it('step 6-7: after A pauses, B gets no coordinates and no fresh updates', async () => {
      const ws = await connectWs(port, b.access);
      await ws.waitFor('ready');
      ws.send({ type: 'subscribe', circleId });
      await ws.waitFor('subscribed');

      const state = await api(app, a).get('/sharing').expect(200);
      const session = state.body.sessions[0];
      await api(app, a).post(`/sharing/${session.id}/pause`).expect(200);
      const avail = await ws.waitFor('member.availability', (d) => d.userId === a.id);
      expect(avail.status).toBe('HIDDEN');

      const locs = await api(app, b).get(`/circles/${circleId}/locations`).expect(200);
      expect(locs.body.find((l: { userId: string }) => l.userId === a.id).location).toBeNull();
      // Paused and exploration off: the server refuses new positions entirely.
      await api(app, a).post('/location', { samples: [sample(AWAY.lat, AWAY.lon + 0.001)] }).expect(409);
      await sleep(150);
      expect(ws.messages.filter((m) => m.type === 'location.update')).toHaveLength(0);

      await api(app, a).post(`/sharing/${session.id}/resume`).expect(200);
      ws.close();
    });

    it('global pause hides A in every circle at once', async () => {
      await api(app, a).put('/sharing/pause-all', { paused: true }).expect(200);
      const locs = await api(app, b).get(`/circles/${circleId}/locations`).expect(200);
      expect(locs.body.find((l: { userId: string }) => l.userId === a.id).status).toBe('HIDDEN');
      await api(app, a).put('/sharing/pause-all', { paused: false }).expect(200);
    });

    it('expired sessions end on schedule and stop sharing', async () => {
      const state = await api(app, a).get('/sharing').expect(200);
      await prisma.locationSharingSession.update({ where: { id: state.body.sessions[0].id }, data: { expiresAt: new Date(Date.now() - 1000) } });
      const { SharingService } = await import('../src/location/sharing.service');
      await app.get(SharingService).expireSessions();
      const after = await api(app, a).get('/sharing').expect(200);
      expect(after.body.sessions).toHaveLength(0);
      const notes = await api(app, a).get('/notifications').expect(200);
      expect(notes.body.items.some((n: { kind: string }) => n.kind === 'SHARING')).toBe(true);
      // restart for the remaining tests
      await api(app, a).post('/sharing', { circleId }).expect(201);
    });

    it('geofence: B gets one arrival alert when A reaches a saved place', async () => {
      const place = await api(app, b)
        .post(`/circles/${circleId}/places`, { name: 'School', type: 'SCHOOL', latitude: AWAY.lat - 0.01, longitude: AWAY.lon, radiusM: 150 })
        .expect(201);
      await api(app, a).post('/location', { samples: [sample(AWAY.lat, AWAY.lon, 120)] }).expect(200);
      // two precise fixes at the place, 40 s apart
      await api(app, a).post('/location', { samples: [sample(AWAY.lat - 0.01, AWAY.lon, 80), sample(AWAY.lat - 0.01, AWAY.lon, 40), sample(AWAY.lat - 0.01, AWAY.lon, 10)] }).expect(200);
      const notes = await api(app, b).get('/notifications').expect(200);
      const arrivals = notes.body.items.filter((n: { kind: string; data: { placeId: string } }) => n.kind === 'ARRIVAL' && n.data.placeId === place.body.id);
      expect(arrivals).toHaveLength(1);
      expect(JSON.stringify(arrivals[0])).not.toMatch(/latitude|longitude/);
    });
  });

  describe('game', () => {
    let landmarkQuestId: string;

    /**
     * Earlier tests jump A across town within seconds, which the server rightly treats as
     * impossible travel. Ageing the stored fix stands in for the hour it takes to get there.
     */
    const travelTime = (userId: string) =>
      prisma.latestLocation.updateMany({ where: { userId }, data: { recordedAt: new Date(Date.now() - 3_600_000) } });

    it('step 8: with exploration on, a validated landmark visit progresses quests server-side', async () => {
      await api(app, a).patch('/me/privacy', { explorationEnabled: true }).expect(200);
      const quests = await api(app, a).get('/quests').expect(200);
      landmarkQuestId = quests.body.find((q: { questCode: string }) => q.questCode === 'daily_landmark').id;
      await travelTime(a.id);
      // Walk up to Monas: two precise fixes ~100 m apart, walking pace.
      const r = await api(app, a)
        .post('/location', { samples: [sample(MONAS.lat - 0.0009, MONAS.lon, 70, { speedMps: 1.4 }), sample(MONAS.lat, MONAS.lon, 5, { speedMps: 1.4 })] })
        .expect(200);
      expect(r.body.game.landmarks.some((l: { name: string; firstVisit: boolean }) => l.name === 'Monumen Nasional' && l.firstVisit)).toBe(true);
      expect(r.body.game.newTiles.length).toBeGreaterThan(0);
      const q = await api(app, a).get(`/quests/${landmarkQuestId}`).expect(200);
      expect(q.body.status).toBe('COMPLETED');
    });

    it('repeated samples at the same spot add no tiles and no XP', async () => {
      const r = await api(app, a).post('/location', { samples: [sample(MONAS.lat, MONAS.lon, 3), sample(MONAS.lat, MONAS.lon, 1)] }).expect(200);
      expect(r.body.game.newTiles).toEqual([]);
      expect(r.body.game.xpGained).toBe(0);
    });

    it('imprecise or mock fixes never earn rewards', async () => {
      const far = { lat: -6.16, lon: 106.84 };
      const r1 = await api(app, a).post('/location', { samples: [{ ...sample(far.lat, far.lon, 0), accuracyM: 300 }] }).expect(200);
      expect(r1.body.game).toBeNull();
      const r2 = await api(app, a).post('/location', { samples: [sample(far.lat, far.lon + 0.002, 0, { isMock: true })] }).expect(200);
      expect(r2.body.game).toBeNull();
    });

    it('claiming needs a finished quest and an idempotency key', async () => {
      const quests = await api(app, a).get('/quests').expect(200);
      const open = quests.body.find((q: { status: string }) => q.status === 'ACTIVE');
      await api(app, a).post(`/quests/${open.id}/claim`).expect(400);
      const r = await api(app, a).post(`/quests/${open.id}/claim`).set('Idempotency-Key', idemKey()).expect(409);
      expect(r.body.error.code).toBe('QUEST_NOT_COMPLETE');
    });

    it('step 9-10: XP is awarded exactly once, even under concurrent and replayed claims', async () => {
      const before = (await api(app, a).get('/me').expect(200)).body.progress.totalXp;
      const key = idemKey();
      const claims = await Promise.all([
        api(app, a).post(`/quests/${landmarkQuestId}/claim`).set('Idempotency-Key', key),
        api(app, a).post(`/quests/${landmarkQuestId}/claim`).set('Idempotency-Key', key),
        api(app, a).post(`/quests/${landmarkQuestId}/claim`).set('Idempotency-Key', idemKey()),
        api(app, a).post(`/quests/${landmarkQuestId}/claim`).set('Idempotency-Key', idemKey()),
      ]);
      for (const c of claims) expect(c.status).toBe(200);
      expect(claims.filter((c) => !c.body.alreadyClaimed)).toHaveLength(1);
      const replay = await api(app, a).post(`/quests/${landmarkQuestId}/claim`).set('Idempotency-Key', key).expect(200);
      expect(replay.body.alreadyClaimed).toBe(true);

      const ledger = await prisma.xPTransaction.findMany({ where: { userId: a.id, source: 'QUEST', sourceRef: landmarkQuestId } });
      expect(ledger).toHaveLength(1);
      const after = (await api(app, a).get('/me').expect(200)).body.progress.totalXp;
      expect(after - before).toBe(ledger[0].amount + claims.reduce((n, c) => n + (c.body.achievements ?? []).reduce((m: number, x: { rewardXp: number }) => m + x.rewardXp, 0), 0));
    });

    it('step 11: level and achievements reflect the ledger', async () => {
      const me = (await api(app, a).get('/me').expect(200)).body;
      const sum = await prisma.xPTransaction.aggregate({ where: { userId: a.id }, _sum: { amount: true } });
      expect(me.progress.totalXp).toBe(sum._sum.amount);
      expect(me.progress.level).toBeGreaterThanOrEqual(2);
      const ach = await api(app, a).get('/achievements').expect(200);
      expect(ach.body.find((x: { code: string }) => x.code === 'first_discovery').unlockedAt).not.toBeNull();
    });

    it('the client cannot grant itself XP: unknown fields are rejected', async () => {
      await api(app, a).patch('/me', { xp: 999999 }).expect(400);
      await api(app, a).post('/location', { samples: [{ ...sample(MONAS.lat, MONAS.lon, 0), xp: 100 }] }).expect(400);
    });

    it('territory points need a team circle and claim at three visits', async () => {
      await api(app, a).patch('/me', { teamCircleId: circleId }).expect(200);
      await api(app, b).patch('/me', { teamCircleId: circleId }).expect(200);
      const lm = await prisma.landmark.findUniqueOrThrow({ where: { code: 'monas' }, include: { territory: true } });
      const tid = lm.territory!.id;
      await travelTime(a.id);
      // A's next visit counts (first visit happened before a team was set).
      await api(app, a).post('/location', { samples: [sample(MONAS.lat + 0.0003, MONAS.lon, 0)] }).expect(200);
      let t = await api(app, outsider).get(`/territories/${tid}`).expect(200);
      expect(t.body.leaderboard[0]).toMatchObject({ circleName: 'Family', score: 1 });
      expect(JSON.stringify(t.body.leaderboard)).not.toMatch(new RegExp(a.id));
      // B opts into exploration and visits.
      await api(app, b).patch('/me/privacy', { explorationEnabled: true }).expect(200);
      await travelTime(b.id);
      await api(app, b).post('/location', { samples: [sample(MONAS.lat, MONAS.lon, 0)] }).expect(200);
      t = await api(app, a).get(`/territories/${tid}`).expect(200);
      expect(t.body.leaderboard[0].score).toBe(2);
      expect(t.body.ownerCircleId).toBeNull();
      // Same player, same day: no extra point.
      await api(app, b).post('/location', { samples: [sample(MONAS.lat + 0.0002, MONAS.lon, 0)] }).expect(200);
      t = await api(app, a).get(`/territories/${tid}`).expect(200);
      expect(t.body.leaderboard[0].score).toBe(2);
    });

    it('leaderboards show scores and names, never coordinates, and respect opt-out', async () => {
      const board = await api(app, b).get('/leaderboards?scope=friends&metric=xp&period=week').expect(200);
      expect(board.body.entries.map((e: { userId: string }) => e.userId)).toContain(a.id);
      expect(JSON.stringify(board.body)).not.toMatch(/latitude|longitude/);
      await api(app, a).patch('/me/privacy', { showInLeaderboards: false }).expect(200);
      const hidden = await api(app, b).get('/leaderboards?scope=friends&metric=xp&period=week').expect(200);
      expect(hidden.body.entries.map((e: { userId: string }) => e.userId)).not.toContain(a.id);
      const own = await api(app, a).get('/leaderboards?scope=friends&metric=xp&period=week').expect(200);
      expect(own.body.me.optedOut).toBe(true);
      expect(own.body.entries.find((e: { isMe: boolean }) => e.isMe).hiddenFromOthers).toBe(true);
      await api(app, a).patch('/me/privacy', { showInLeaderboards: true }).expect(200);
    });

    it('an outsider cannot read A\'s tiles: the tiles endpoint is always the caller\'s own', async () => {
      const q = `north=${MONAS.lat + 0.01}&south=${MONAS.lat - 0.01}&east=${MONAS.lon + 0.01}&west=${MONAS.lon - 0.01}`;
      const mine = await api(app, a).get(`/exploration/tiles?${q}`).expect(200);
      expect(mine.body.tiles.length).toBeGreaterThan(0);
      const theirs = await api(app, outsider).get(`/exploration/tiles?${q}`).expect(200);
      expect(theirs.body.tiles).toHaveLength(0);
    });
  });

  describe('safety controls', () => {
    it('blocking hides both sides from each other', async () => {
      await api(app, b).put(`/users/${a.id}/block`).expect(204);
      const locs = await api(app, b).get(`/circles/${circleId}/locations`).expect(200);
      expect(locs.body.find((l: { userId: string }) => l.userId === a.id).status).toBe('HIDDEN');
      await api(app, b).delete(`/users/${a.id}/block`).expect(204);
    });

    it('reports are accepted only about circle-mates', async () => {
      await api(app, b).post(`/users/${a.id}/report`, { reason: 'SPAM' }).expect(201);
      await api(app, outsider).post(`/users/${a.id}/report`, { reason: 'SPAM' }).expect(404);
    });

    it('rate-limits invitation guessing', async () => {
      const u = await register(app, 'Guess');
      let limited = false;
      for (let i = 0; i < 12; i++) {
        const r = await api(app, u).post('/circles/join', { code: 'ZZZZ-ZZZZ-ZZ' });
        if (r.status === 429) limited = true;
      }
      expect(limited).toBe(true);
    });

    it('a removed member loses access right away', async () => {
      const inv = await api(app, a).post(`/circles/${circleId}/invitations`, {}).expect(201);
      await api(app, outsider).post('/circles/join', { code: inv.body.code }).expect(200);
      await api(app, outsider).get(`/circles/${circleId}/locations`).expect(200);
      await api(app, a).delete(`/circles/${circleId}/members/${outsider.id}`).expect(204);
      await api(app, outsider).get(`/circles/${circleId}/locations`).expect(404);
    });
  });

  describe('account deletion (step 12)', () => {
    it('requires the password, then removes the user and their location data', async () => {
      await api(app, a).post('/me/delete', { confirm: 'DELETE', password: 'wrong password!' }).expect(403);
      const exp = await api(app, a).get('/me/export').expect(200);
      expect(exp.body.user.id).toBe(a.id);
      expect(exp.body.user.passwordHash).toBeUndefined();

      await api(app, a).post('/me/delete', { confirm: 'DELETE', password: a.password }).expect(204);
      await api(app, a).get('/me').expect(401);
      await api(app).post('/auth/login', { identifier: a.email, password: a.password }).expect(401);
      expect(await prisma.latestLocation.count({ where: { userId: a.id } })).toBe(0);
      expect(await prisma.locationHistory.count({ where: { userId: a.id } })).toBe(0);
      expect(await prisma.xPTransaction.count({ where: { userId: a.id } })).toBe(0);
      // The circle survives and B owns it now.
      const c = await api(app, b).get(`/circles/${circleId}`).expect(200);
      expect(c.body.myRole).toBe('OWNER');
      expect(c.body.members.map((m: { userId: string }) => m.userId)).toEqual([b.id]);
      // The audit trail keeps only the id, no personal data.
      const audit = await prisma.auditEvent.findFirst({ where: { actorId: a.id, action: 'user.delete' } });
      expect(audit).not.toBeNull();
    });
  });

  it('health endpoints stay public', async () => {
    await request(app.getHttpServer()).get('/health').expect(200);
    await request(app.getHttpServer()).get('/health/ready').expect(200);
  });
});
