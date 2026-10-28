# Privacy and safety

Location is the most sensitive data Locana handles. This document describes what the code does. It is not legal advice, and no legal review has happened yet.

## Principles in the code

- **Opt-in, per circle, with an end.** Sharing starts only when the user starts a session for a specific circle (1 hour, 8 hours or until stopped). Login, reinstall, an app update or granting a permission never starts it. Server-side, sessions expire on schedule and the owner is notified.
- **One switch to disappear.** "Pause all sharing" hides the user from every circle at once; "Hide from one person" works without leaving the circle; blocking hides both people from each other everywhere.
- **Visible while active.** The foreground service notification is shown whenever the device collects location and has a "Pause sharing" action. The app never suppresses Android's location indicator.
- **Server decides who sees what.** Every read and every realtime event checks membership, an active unexpired session, global pause, per-member hiding and blocks at that moment. Unauthorized requests get 404 so ids cannot be probed.
- **Collect only what a feature needs.** With sharing and exploration both off, the server refuses location uploads (`409 LOCATION_NOT_NEEDED`) and deletes the stored latest position.
- **No coordinates where they are not needed.** Feeds, notifications, leaderboards and territory pages carry names and ids only. Territory history shows days, not times. Logs never contain request bodies or coordinates.

## Data categories

| Data | Stored where | Who can access | Purpose | Retention |
| --- | --- | --- | --- | --- |
| E-mail, username, display name, bio | `User`, `UserProfile` | The user; circle-mates see display name, username and avatar | Account, identification in circles | Until account deletion |
| Password hash (Argon2id) | `User` | Nobody can read it back | Sign-in | Until deletion or change |
| Refresh token hashes, device names | `UserSession` | The user (device list) | Sessions, revocation | Usable for 30 days; rows deleted 7 days after expiry or revocation |
| Latest position (lat, lon, accuracy, speed, bearing, battery) | `LatestLocation` | The user; members allowed by the rules above | Live map | Overwritten by each new fix; deleted when sharing and exploration are both off, and on account deletion |
| Location history | `LocationHistory` | The user only | Personal trail (opt-in) | User's choice 1-30 days, capped by `LOCATION_HISTORY_RETENTION_DAYS`; deleted when turned off |
| Saved places | `SavedPlace` | Members of that circle | Arrival and departure alerts | Until deleted, or the circle is deleted |
| Geofence state and events | `GeofenceState`, `GeofenceEvent` | Server only (alerts go to authorized members) | Duplicate suppression | Until the place or account is deleted |
| Exploration tiles, landmark visits | `TileDiscovery`, `LandmarkVisit` | The user only (circles see totals if allowed) | Game | Until account deletion |
| XP ledger, quests, achievements, collectibles | game tables | The user; rankings show totals | Game | Until account deletion |
| Territory scores | `TerritoryScore`, `TerritoryEvent` | Public: circle names and scores | Game | Kept for history; personal ids removed on deletion |
| Risk signals | `RiskSignal` | Admins | Anti-cheat review | Until account deletion (add a time limit before launch) |
| Reports | `AbuseReport` | Admins | Safety | Until resolved and archived (define a period) |
| Audit events | `AuditEvent` | Admins | Security investigations | Keeps the user id after deletion, no personal content (define a period) |
| On the phone | Room queue, cache, DataStore | The device user | Offline queue, faster screens | Queue up to 23 h; cleared on sign-out; excluded from backups |

## Account deletion

`POST /v1/me/delete` (password confirmation for password accounts) stops all sharing, leaves every circle (ownership passes to the longest-standing admin or member; empty circles are deleted), then deletes the user row. Foreign keys cascade to sessions, latest location, history, places the user created, geofence data, tiles, visits, quests, XP, achievements, collectibles, notifications, devices, feed items and reactions. Territory events keep the circle and score but drop the user id. Backups must follow the same retention (see `docs/backend.md`).

## Export

`GET /v1/me/export` returns all of the above for the requesting user as JSON, without password or Google identifiers. The app saves it through the system share sheet.

## Game safety rules

- Rewards come only from public landmarks curated by admins, never from other people's positions or private homes.
- No mechanic rewards following, finding or approaching a specific person. Territories score per circle and never expose who was where.
- Walking quests ignore vehicle speeds and default to modest distances (1.5 km daily); challenge copy reminds people to pick goals everyone can reach.
- Landmark lists remind players to respect opening hours and never enter restricted areas.

## Indonesia: UU PDP (Law No. 27 of 2022)

Locana processes precise location, which UU PDP treats as personal data, and location linked to people's routines may be considered specific (sensitive) data. Points that need a decision and legal review before launch:

- Lawful basis: explicit consent for sharing and exploration (the app collects it through explicit opt-in actions; record consent versions and timestamps in the audit log).
- Privacy notice in Bahasa Indonesia covering purpose, retention, recipients and rights.
- Data subject rights: access and portability (export exists), correction (profile editing exists), deletion (exists), withdrawal of consent (stopping sharing and exploration exists).
- Data protection impact assessment, likely required for large-scale processing of location data.
- Breach notification within 3 x 24 hours to the authority and affected people: needs an incident process.
- Data location and transfer if hosting outside Indonesia.
- A data protection officer may be required depending on scale.
- Minors: the app has no age gate yet. Decide on a minimum age and parental consent before launch.

## Google Play

See `docs/android.md` for the background location and foreground service declarations and the Data safety form.

## Not claimed

Locana does not claim compliance with UU PDP, GDPR, Google Play policies or any certification. The items above are open work.
