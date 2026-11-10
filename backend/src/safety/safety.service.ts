import { HttpStatus, Injectable, Logger } from '@nestjs/common';
import { MailService } from '../auth/mail.service';
import { CircleAccessService } from '../circles/circle-access.service';
import { ApiError, badRequest } from '../common/errors';
import { NotificationsService } from '../notifications/notifications.service';
import { PrismaService } from '../prisma/prisma.service';
import { buildTimeline, TimelinePoint } from '../location/timeline';
import { EmergencyContactDto, QuickMessageDto, SosDto } from './safety.dto';
import { COLLECTIBLE_QUICK_MESSAGES, QUICK_MESSAGES, quickMessage } from './quick-messages';

/** A trip counts as long at this distance, or at this length of time once it has covered a few km. */
export const LONG_TRIP_M = 15_000;
export const LONG_TRIP_MIN_S = 40 * 60;
export const LONG_TRIP_MIN_DISTANCE_M = 5_000;

@Injectable()
export class SafetyService {
  private readonly log = new Logger('Safety');

  constructor(
    private readonly prisma: PrismaService,
    private readonly access: CircleAccessService,
    private readonly notifications: NotificationsService,
    private readonly mail: MailService,
  ) {}

  // ── emergency contacts ──
  async contacts(userId: string) {
    const rows = await this.prisma.emergencyContact.findMany({ where: { userId }, orderBy: { createdAt: 'asc' } });
    return rows.map((c) => ({ id: c.id, name: c.name, phone: c.phone, email: c.email }));
  }

  async setContacts(userId: string, list: EmergencyContactDto[]) {
    await this.prisma.$transaction([
      this.prisma.emergencyContact.deleteMany({ where: { userId } }),
      this.prisma.emergencyContact.createMany({ data: list.map((c) => ({ userId, name: c.name, phone: c.phone, email: c.email || null })) }),
    ]);
    return this.contacts(userId);
  }

  // ── SOS ──
  /**
   * Tells everyone in the person's circles and every emergency contact with an e-mail address, and returns the
   * contacts' phone numbers so the phone can place the call. This is the one place coordinates travel in a
   * notification: someone asking for help has chosen to be found.
   */
  async sos(userId: string, dto: SosDto) {
    const [user, contacts] = await Promise.all([
      this.prisma.user.findUniqueOrThrow({ where: { id: userId }, include: { profile: true } }),
      this.prisma.emergencyContact.findMany({ where: { userId } }),
    ]);
    const name = user.profile?.displayName ?? user.username;
    const mates = await this.access.circleMates(userId);
    const members = mates.length
      ? await this.prisma.user.findMany({ where: { id: { in: mates }, deletedAt: null }, select: { id: true, email: true } })
      : [];
    const event = await this.prisma.sosEvent.create({
      data: { userId, reason: dto.reason, latitude: dto.latitude, longitude: dto.longitude, accuracyM: dto.accuracyM ?? null },
    });
    const mapsUrl = `https://www.google.com/maps?q=${dto.latitude},${dto.longitude}`;
    const why = dto.reason === 'CRASH' ? 'A hard impact was detected and they did not answer within 15 seconds.' : 'They held the SOS button.';
    let notified = 0;
    for (const m of members) {
      const n = await this.notifications.notify(
        m.id,
        'SOS',
        `${name} needs help`,
        why,
        { sosId: event.id, userId, reason: dto.reason, latitude: dto.latitude, longitude: dto.longitude, mapsUrl },
      );
      if (n) notified++;
    }
    const subject = `${name} sent an SOS from Locana`;
    const text =
      `${name} needs help. ${why}\n\n` +
      `Last position: ${mapsUrl}\n` +
      `Sent at: ${event.createdAt.toISOString()}\n\n` +
      'Please try to reach them now. If you cannot, contact local emergency services.\n';
    const recipients = new Set<string>();
    for (const m of members) if (m.email) recipients.add(m.email.toLowerCase());
    for (const c of contacts) if (c.email) recipients.add(c.email.toLowerCase());
    recipients.delete(user.email.toLowerCase());
    let emailed = 0;
    await Promise.all(
      [...recipients].map(async (to) => {
        try {
          await this.mail.send(to, subject, text);
          emailed++;
        } catch (e) {
          this.log.error(`SOS e-mail to a recipient failed: ${(e as Error).message}`);
        }
      }),
    );
    await this.prisma.sosEvent.update({ where: { id: event.id }, data: { notified, emailed } });
    await this.prisma.auditEvent.create({ data: { actorId: userId, action: 'safety.sos', meta: { reason: dto.reason, notified, emailed } } });
    return { id: event.id, notified, emailed, contacts: contacts.map((c) => ({ name: c.name, phone: c.phone })) };
  }

  // ── quick messages ──
  /** The everyday messages plus the funny ones this person has earned. */
  async catalog(userId: string) {
    const owned = await this.prisma.userCollectible.findMany({ where: { userId, collectible: { kind: 'QUICK_MESSAGE' } }, select: { collectible: { select: { code: true } } } });
    const codes = new Set(owned.map((o) => o.collectible.code));
    return [...QUICK_MESSAGES, ...COLLECTIBLE_QUICK_MESSAGES.filter((m) => m.collectibleCode && codes.has(m.collectibleCode))].map(({ code, text, emoji }) => ({ code, text, emoji }));
  }

  async sendQuickMessage(circleId: string, fromId: string, toId: string, dto: QuickMessageDto) {
    const def = quickMessage(dto.code);
    if (!def) throw badRequest('UNKNOWN_QUICK_MESSAGE', 'That quick message does not exist');
    if (def.collectibleCode && !(await this.prisma.userCollectible.findFirst({ where: { userId: fromId, collectible: { code: def.collectibleCode } } }))) {
      throw new ApiError(HttpStatus.FORBIDDEN, 'NOT_UNLOCKED', 'You have not unlocked that message yet. Check your collection.');
    }
    if (fromId === toId) throw badRequest('NOT_YOURSELF', 'Pick someone else in the circle');
    await this.access.requireMember(circleId, fromId);
    await this.access.requireMember(circleId, toId);
    if (!(await this.access.circleMates(fromId)).includes(toId)) throw new ApiError(HttpStatus.FORBIDDEN, 'NOT_ALLOWED', 'You cannot message this person');
    const since = new Date(Date.now() - 5_000);
    const recent = await this.prisma.quickMessage.count({ where: { fromUserId: fromId, toUserId: toId, createdAt: { gt: since } } });
    if (recent > 0) throw new ApiError(HttpStatus.TOO_MANY_REQUESTS, 'TOO_FAST', 'Wait a few seconds before sending another');
    const hour = await this.prisma.quickMessage.count({ where: { fromUserId: fromId, createdAt: { gt: new Date(Date.now() - 3_600_000) } } });
    if (hour >= 60) throw new ApiError(HttpStatus.TOO_MANY_REQUESTS, 'TOO_MANY', 'That is a lot of messages for one hour');
    await this.prisma.quickMessage.create({ data: { fromUserId: fromId, toUserId: toId, circleId, code: def.code } });
    const sender = await this.prisma.userProfile.findUniqueOrThrow({ where: { userId: fromId } });
    const n = await this.notifications.notify(toId, 'QUICK_MESSAGE', `${sender.displayName}: ${def.text} ${def.emoji}`, '', {
      circleId,
      fromUserId: fromId,
      fromName: sender.displayName,
      code: def.code,
      text: def.text,
      emoji: def.emoji,
    });
    return { sent: true, delivered: n !== null, code: def.code, text: def.text, emoji: def.emoji };
  }

  // ── long trips ──
  /**
   * After an upload: if the person's current trip has become long, tell the people who can see them, once per trip.
   * Names and distances only, never coordinates.
   */
  async checkLongTrip(userId: string, now = new Date()) {
    const rows = await this.prisma.locationHistory.findMany({
      where: { userId, recordedAt: { gte: new Date(now.getTime() - 6 * 3_600_000) } },
      orderBy: { recordedAt: 'asc' },
      select: { latitude: true, longitude: true, accuracyM: true, speedMps: true, activity: true, recordedAt: true },
      take: 2_000,
    });
    if (rows.length < 3) return false;
    const segs = buildTimeline(rows as TimelinePoint[]);
    const last = segs[segs.length - 1];
    if (!last || last.type !== 'TRIP') return false;
    if (now.getTime() - last.endedAt.getTime() > 6 * 60_000) return false; // already over
    const long = last.distanceM >= LONG_TRIP_M || (last.durationSec >= LONG_TRIP_MIN_S && last.distanceM >= LONG_TRIP_MIN_DISTANCE_M);
    if (!long) return false;
    try {
      await this.prisma.tripAlert.create({ data: { userId, startedAt: last.startedAt } });
    } catch {
      return false; // already told about this trip
    }
    const profile = await this.prisma.userProfile.findUnique({ where: { userId } });
    const name = profile?.displayName ?? 'Someone';
    const viewers = await this.access.locationViewers(userId, now);
    const km = Math.round(last.distanceM / 100) / 10;
    const how = last.mode === 'DRIVE' ? 'driving' : last.mode === 'CYCLE' ? 'cycling' : 'walking';
    const told = new Set<string>();
    for (const [circleId, recipients] of viewers) {
      for (const r of recipients) {
        if (told.has(r)) continue;
        told.add(r);
        await this.notifications.notify(r, 'TRIP', `${name} is on a long trip`, `${how}, ${km} km so far`, { circleId, userId, km, mode: last.mode });
      }
    }
    return true;
  }
}
