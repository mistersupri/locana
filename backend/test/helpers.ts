import { INestApplication } from '@nestjs/common';
import { randomUUID } from 'crypto';
import request from 'supertest';
import WebSocket from 'ws';
import { createApp } from '../src/main';

export interface TestUser {
  id: string;
  username: string;
  email: string;
  password: string;
  access: string;
  refresh: string;
}

export async function startApp(): Promise<{ app: INestApplication; port: number }> {
  const app = await createApp();
  await app.listen(0, '127.0.0.1');
  const addr = app.getHttpServer().address();
  return { app, port: addr.port };
}

let counter = 0;
export async function register(app: INestApplication, name: string): Promise<TestUser> {
  counter += 1;
  const tag = `${name.toLowerCase()}${Date.now() % 100000}${counter}`;
  const body = { email: `${tag}@example.test`, password: 'correct horse battery', username: tag, displayName: name, deviceName: 'jest' };
  const res = await request(app.getHttpServer()).post('/v1/auth/register').send(body).expect(201);
  return { id: res.body.user.id, username: tag, email: body.email, password: body.password, access: res.body.tokens.accessToken, refresh: res.body.tokens.refreshToken };
}

export const api = (app: INestApplication, u?: TestUser) => {
  const s = request(app.getHttpServer());
  const auth = (r: request.Test) => (u ? r.set('Authorization', `Bearer ${u.access}`) : r);
  return {
    get: (p: string) => auth(s.get(`/v1${p}`)),
    post: (p: string, body?: object) => auth(s.post(`/v1${p}`)).send(body ?? {}),
    put: (p: string, body?: object) => auth(s.put(`/v1${p}`)).send(body ?? {}),
    patch: (p: string, body?: object) => auth(s.patch(`/v1${p}`)).send(body ?? {}),
    delete: (p: string) => auth(s.delete(`/v1${p}`)),
  };
};

let seq = 0;
/** A precise fix a few seconds in the past. */
export function sample(lat: number, lon: number, secondsAgo = 5, extra: object = {}) {
  seq += 1;
  return { latitude: lat, longitude: lon, accuracyM: 8, recordedAt: new Date(Date.now() - secondsAgo * 1000).toISOString(), sequence: seq, source: 'fused', ...extra };
}

export interface WsClient {
  ws: WebSocket;
  messages: { type: string; data: any }[];
  waitFor: (type: string, pred?: (d: any) => boolean, ms?: number) => Promise<any>;
  send: (m: object) => void;
  close: () => void;
}

export function connectWs(port: number, token?: string): Promise<WsClient> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${port}/ws`, token ? { headers: { Authorization: `Bearer ${token}` } } : {});
    const messages: WsClient['messages'] = [];
    const waiters: { type: string; pred: (d: any) => boolean; resolve: (d: any) => void }[] = [];
    ws.on('message', (raw) => {
      const m = JSON.parse(raw.toString());
      messages.push(m);
      for (const w of [...waiters]) {
        if (w.type === m.type && w.pred(m.data)) {
          waiters.splice(waiters.indexOf(w), 1);
          w.resolve(m.data);
        }
      }
    });
    const client: WsClient = {
      ws,
      messages,
      send: (m) => ws.send(JSON.stringify(m)),
      close: () => ws.close(),
      waitFor: (type, pred = () => true, ms = 3000) =>
        new Promise((res, rej) => {
          const hit = messages.find((m) => m.type === type && pred(m.data));
          if (hit) return res(hit.data);
          const w = { type, pred, resolve: res };
          waiters.push(w);
          setTimeout(() => {
            const i = waiters.indexOf(w);
            if (i >= 0) {
              waiters.splice(i, 1);
              rej(new Error(`timed out waiting for ${type}`));
            }
          }, ms);
        }),
    };
    ws.on('open', () => resolve(client));
    ws.on('error', reject);
  });
}

export const idemKey = () => randomUUID().replace(/-/g, '');
export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
