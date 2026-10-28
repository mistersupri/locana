# Backend guide

## Requirements

Node.js 22, PostgreSQL 16, Redis 7 (optional for one instance, required for several), Docker for the container path.

## Local development

```bash
cd backend && npm ci
```

```bash
cp .env.example .env
```

Edit `.env` (see the reference below), then create the schema and load game configuration:

```bash
npx prisma migrate deploy
```

```bash
npm run seed
```

```bash
npm run start:dev
```

The API listens on `http://localhost:3000`. Swagger UI: `http://localhost:3000/docs`. Health: `/health` (liveness) and `/health/ready` (database and Redis).

### Make yourself an admin

Register through the app or `POST /v1/auth/register`, copy your user id from `GET /v1/me`, add it to `ADMIN_USER_IDS` and restart.

## Environment variables

| Variable | Required | Default | Notes |
| --- | --- | --- | --- |
| `NODE_ENV` | no | `development` | `production` disables Swagger, requires SMTP and a 32+ character JWT secret, and ignores `.env` (pass variables from the platform) |
| `PORT` | no | `3000` | |
| `DATABASE_URL` | yes | | `postgresql://user:pass@host:5432/locana?schema=public` |
| `REDIS_URL` | for more than one instance | empty | Empty means in-process rate limiting and event fan-out |
| `JWT_ACCESS_SECRET` | yes | | `openssl rand -base64 48` |
| `ACCESS_TOKEN_TTL_SECONDS` | no | `900` | |
| `REFRESH_TOKEN_TTL_DAYS` | no | `30` | |
| `GOOGLE_CLIENT_ID` | for Google Sign-In | empty | OAuth client id of type "Web application"; the Android app sends it as `serverClientId` |
| `SMTP_URL` | in production | empty | e.g. `smtps://user:pass@smtp.example.com:465`; in development reset codes are logged |
| `MAIL_FROM` | no | `Locana <no-reply@example.com>` | |
| `LOCATION_HISTORY_RETENTION_DAYS` | no | `30` | Server-wide maximum; users can choose less |
| `GAME_TZ_OFFSET_MINUTES` | no | `420` | When game days and weeks roll over (420 = WIB) |
| `ADMIN_USER_IDS` | no | empty | Comma-separated user ids |
| `CORS_ORIGINS` | no | empty | Only for browser-based admin tools; the mobile app does not need CORS |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Compose only | | Used by `docker-compose.yml` |

Never commit `.env`. It is in `.gitignore`.

## Tests

Unit tests (no database, about 2 s):

```bash
npm test
```

End-to-end tests need PostgreSQL. They rebuild a separate database named in `TEST_DATABASE_URL` (default `postgresql://locana:locana@localhost:5432/locana_test`) from migrations and seed it; the name must contain `test`:

```bash
npm run test:e2e
```

Type check:

```bash
npm run lint
```

Last run in this repository: 38 unit tests and 34 end-to-end tests passed. The e2e suite covers the PRD's 12-step scenario, refresh token reuse, logout, password reset, outsider access (REST and WebSocket), pause and hide, stale updates, geofence alerts, idempotent and concurrent claims, ledger integrity, leaderboard opt-out, blocking, reports, invitation rate limiting, member removal, account deletion and territory concurrency.

## Production deployment

The repository ships a `Dockerfile` (multi-stage, runs as `node`, `tini` as PID 1 for clean shutdown, health check on `/health/ready`) and a `docker-compose.yml` with PostgreSQL, Redis, a one-shot `migrate` service and the API. The Compose file suits a single host; on a managed platform run the same image with managed PostgreSQL and Redis.

1. Put the API behind a TLS-terminating reverse proxy (Caddy, nginx, a cloud load balancer). It must forward WebSocket upgrades on `/ws` and set `X-Forwarded-For`; the app trusts exactly one proxy hop for client IPs.
2. Set every required variable as a platform secret. Use `NODE_ENV=production`.
3. Run migrations before starting new API containers: `docker compose run --rm migrate` or `npx prisma migrate deploy` in a release job.
4. Seed or update game configuration with `node dist/scripts/seed.js` (idempotent; keeps existing event windows).
5. Scale API containers horizontally once `REDIS_URL` is set. Scheduled jobs (session expiry, retention, territory decay, weekly snapshots) are safe to run on every instance: each job uses conditional updates so the same row is processed once.

Minimal Caddy example:

```
api.example.com {
    reverse_proxy api:3000
}
```

Caddy proxies WebSocket upgrades by default.

## Safe migrations

- Create migrations in development with `npx prisma migrate dev --name <change>` and commit the generated SQL.
- Prefer additive changes (new nullable columns, new tables). For renames or type changes, add the new column, backfill, deploy code that reads both, then drop the old column in a later release.
- Back up before deploying a migration that drops or rewrites data.
- `prisma migrate deploy` never resets data; `migrate reset` does and must never run against production.

## Backup and restore

Back up daily and before migrations:

```bash
docker compose exec -T db pg_dump -U locana -Fc locana > locana-$(date +%F).dump
```

Restore into an empty database:

```bash
docker compose exec -T db pg_restore -U locana -d locana --clean --if-exists < locana-2026-10-28.dump
```

Backups contain location data. Encrypt them at rest, limit who can read them, and delete them on the same schedule as your retention policy (a 30-day history limit means little if backups live for a year). Redis holds only rate-limit counters and pub/sub traffic and needs no backup.

## Operations

- Logs go to stdout. Request bodies, coordinates and tokens are never logged; errors log stack traces without payloads.
- `GET /v1/admin/risk` shows anti-cheat signals per user for the last 7 days. Signals reduce rewards for individual samples; they never ban anyone automatically. Review before acting.
- `GET /v1/admin/audit` lists security-relevant events (logins, password changes, sharing start/stop, role changes, invitation creation, deletions, exports).

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| Boot fails with `Missing required environment variable` | `.env` not loaded or variable empty; in production `.env` is ignored on purpose |
| `JWT_ACCESS_SECRET must be at least 32 characters in production` | Generate one with `openssl rand -base64 48` |
| `/health/ready` returns 503 with `redis: false` | `REDIS_URL` set but Redis unreachable; unset it for a single instance |
| The app's WebSocket connects then closes with 4401 | Access token expired or session revoked; the client should refresh and send `auth` |
| Locations upload but nobody sees them | The sharer has no ACTIVE session for that circle, paused globally, hid from that viewer, or one blocked the other. `GET /v1/sharing` as the sharer shows which |
| Quests never progress | Exploration is off, fixes are less accurate than 50 m, or the device reports a mock provider |
| Territory points never count | The player has no team circle set (`PATCH /v1/me {teamCircleId}`) |
| `P2002` unique constraint errors in logs | Usually a double submit that the API already answers with 409 or an idempotent result |
