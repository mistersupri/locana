import { Logger, OnModuleInit } from '@nestjs/common';
import { OnGatewayConnection, OnGatewayDisconnect, WebSocketGateway } from '@nestjs/websockets';
import { IncomingMessage } from 'http';
import { WebSocket } from 'ws';
import { bearer } from '../auth/auth.guard';
import { TokenService } from '../auth/token.service';
import { CircleAccessService } from '../circles/circle-access.service';
import { RateLimiter } from '../common/rate-limit';
import { RealtimeBus, RealtimeEnvelope } from './realtime-bus';

interface Client {
  socket: WebSocket;
  userId: string | null;
  sessionId: string | null;
  token: string | null;
  circles: Set<string>;
  alive: boolean;
  authTimer?: NodeJS.Timeout;
}

/**
 * WebSocket endpoint at /ws.
 *
 * Auth: `Authorization: Bearer <access token>` on the upgrade request (OkHttp sets it), or a
 * first message {"type":"auth","token":"..."} within 5 s. Tokens never go in the URL.
 *
 * Client → server: auth, subscribe {circleId}, unsubscribe {circleId}, ping.
 * Server → client: {type, data, ts} where type is a RealtimeEventType, plus ready/subscribed/error/pong.
 *
 * Every subscribe is checked against membership, and every outgoing event is addressed to an
 * explicit recipient list computed from current permissions. Location events also require the
 * socket to be subscribed to that circle.
 */
@WebSocketGateway({ path: '/ws' })
export class RealtimeGateway implements OnGatewayConnection, OnGatewayDisconnect, OnModuleInit {
  private readonly log = new Logger('Realtime');
  private readonly clients = new Set<Client>();
  private readonly byUser = new Map<string, Set<Client>>();
  private readonly bySocket = new WeakMap<WebSocket, Client>();
  private heartbeat?: NodeJS.Timeout;

  constructor(
    private readonly tokens: TokenService,
    private readonly access: CircleAccessService,
    private readonly bus: RealtimeBus,
    private readonly limiter: RateLimiter,
  ) {}

  onModuleInit() {
    this.bus.setDeliver((e) => this.deliver(e));
    // Ping every 25 s; drop sockets that missed one. Keeps NAT mappings alive on mobile networks.
    this.heartbeat = setInterval(() => {
      for (const c of this.clients) {
        if (!c.alive) {
          c.socket.terminate();
          continue;
        }
        c.alive = false;
        c.socket.ping();
        // Logout, password change or token expiry ends the socket. Clients re-send
        // {"type":"auth"} with a fresh token after every refresh.
        if (c.token) void this.tokens.verifyAccess(c.token).catch(() => c.socket.close(4401, 'session ended'));
      }
    }, 25_000);
    this.heartbeat.unref();
  }

  async handleConnection(socket: WebSocket, req: IncomingMessage) {
    const client: Client = { socket, userId: null, sessionId: null, token: null, circles: new Set(), alive: true };
    this.clients.add(client);
    this.bySocket.set(socket, client);
    socket.on('pong', () => (client.alive = true));
    socket.on('message', (raw) => void this.onMessage(client, raw.toString()));
    const token = bearer(req.headers.authorization);
    if (token) await this.authenticate(client, token);
    else client.authTimer = setTimeout(() => !client.userId && socket.close(4401, 'auth timeout'), 5_000);
  }

  handleDisconnect(socket: WebSocket) {
    const c = this.bySocket.get(socket);
    if (!c) return;
    clearTimeout(c.authTimer);
    this.clients.delete(c);
    if (c.userId) this.byUser.get(c.userId)?.delete(c);
  }

  private send(c: Client, type: string, data: unknown) {
    if (c.socket.readyState === WebSocket.OPEN) c.socket.send(JSON.stringify({ type, data, ts: new Date().toISOString() }));
  }

  private async authenticate(c: Client, token: string) {
    try {
      const v = await this.tokens.verifyAccess(token);
      clearTimeout(c.authTimer);
      if (c.userId && c.userId !== v.userId) throw new Error('user mismatch');
      c.userId = v.userId;
      c.sessionId = v.sessionId;
      c.token = token;
      if (!this.byUser.has(v.userId)) this.byUser.set(v.userId, new Set());
      this.byUser.get(v.userId)!.add(c);
      this.send(c, 'ready', { userId: v.userId });
    } catch {
      this.send(c, 'error', { code: 'UNAUTHENTICATED' });
      c.socket.close(4401, 'unauthenticated');
    }
  }

  private async onMessage(c: Client, raw: string) {
    if (raw.length > 4_096) return c.socket.close(4400, 'message too large');
    let msg: { type?: string; token?: string; circleId?: string };
    try {
      msg = JSON.parse(raw);
    } catch {
      return this.send(c, 'error', { code: 'BAD_JSON' });
    }
    if (msg.type === 'auth' && typeof msg.token === 'string') return this.authenticate(c, msg.token);
    if (!c.userId) return this.send(c, 'error', { code: 'UNAUTHENTICATED' });
    try {
      await this.limiter.hit(`ws:${c.userId}`, 120, 60);
    } catch {
      return this.send(c, 'error', { code: 'RATE_LIMITED' });
    }
    switch (msg.type) {
      case 'ping':
        return this.send(c, 'pong', {});
      case 'subscribe': {
        if (typeof msg.circleId !== 'string') return this.send(c, 'error', { code: 'BAD_REQUEST' });
        try {
          await this.access.requireMember(msg.circleId, c.userId);
          c.circles.add(msg.circleId);
          return this.send(c, 'subscribed', { circleId: msg.circleId });
        } catch {
          return this.send(c, 'error', { code: 'CIRCLE_NOT_FOUND', circleId: msg.circleId });
        }
      }
      case 'unsubscribe':
        if (typeof msg.circleId === 'string') c.circles.delete(msg.circleId);
        return this.send(c, 'unsubscribed', { circleId: msg.circleId });
      default:
        return this.send(c, 'error', { code: 'UNKNOWN_TYPE' });
    }
  }

  private deliver(e: RealtimeEnvelope) {
    for (const userId of e.recipients) {
      const sockets = this.byUser.get(userId);
      if (!sockets) continue;
      for (const c of sockets) {
        if (e.circleId && !c.circles.has(e.circleId)) continue;
        // Losing membership ends the subscription on this socket too.
        if (e.type === 'circle.membership') {
          const d = e.data as { circleId: string; userId?: string; change: string };
          if ((d.change === 'REMOVED' || d.change === 'LEFT') && d.userId === userId) c.circles.delete(d.circleId);
          if (d.change === 'DELETED') c.circles.delete(d.circleId);
        }
        this.send(c, e.type, e.data);
      }
    }
  }

  /** Test hook and ops metric. */
  connectedUsers(): number {
    return this.byUser.size;
  }
}
