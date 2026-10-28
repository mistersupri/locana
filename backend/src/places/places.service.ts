import { Injectable } from '@nestjs/common';
import { SavedPlace } from '@prisma/client';
import { CircleAccessService } from '../circles/circle-access.service';
import { FeedService } from '../circles/feed.service';
import { badRequest, notFound } from '../common/errors';
import { NotificationsService } from '../notifications/notifications.service';
import { PrismaService } from '../prisma/prisma.service';
import { RealtimeBus } from '../realtime/realtime-bus';
import { evaluateGeofence, FenceSample } from './geofence-engine';
import { PlaceDto, UpdatePlaceDto } from './places.dto';

const MAX_PLACES_PER_CIRCLE = 30;

@Injectable()
export class PlacesService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly access: CircleAccessService,
    private readonly notifications: NotificationsService,
    private readonly feed: FeedService,
    private readonly bus: RealtimeBus,
  ) {}

  private view(p: SavedPlace) {
    return {
      id: p.id,
      circleId: p.circleId,
      name: p.name,
      type: p.type,
      latitude: p.latitude,
      longitude: p.longitude,
      radiusM: p.radiusM,
      notifyArrive: p.notifyArrive,
      notifyLeave: p.notifyLeave,
      notifyUserIds: p.notifyUserIds,
      createdById: p.createdById,
      updatedAt: p.updatedAt,
    };
  }

  /** Places are visible to members of their circle only. */
  async list(circleId: string, userId: string) {
    await this.access.requireMember(circleId, userId);
    const rows = await this.prisma.savedPlace.findMany({ where: { circleId }, orderBy: { createdAt: 'asc' } });
    return rows.map((p) => this.view(p));
  }

  private async validRecipients(circleId: string, ids: string[] | undefined) {
    if (!ids || ids.length === 0) return [];
    const members = await this.prisma.circleMembership.findMany({ where: { circleId, userId: { in: ids } }, select: { userId: true } });
    if (members.length !== new Set(ids).size) throw badRequest('INVALID_RECIPIENTS', 'Alert recipients must be members of this circle');
    return [...new Set(ids)];
  }

  async create(circleId: string, userId: string, dto: PlaceDto) {
    await this.access.requireMember(circleId, userId);
    if ((await this.prisma.savedPlace.count({ where: { circleId } })) >= MAX_PLACES_PER_CIRCLE) {
      throw badRequest('TOO_MANY_PLACES', `A circle can save up to ${MAX_PLACES_PER_CIRCLE} places`);
    }
    const p = await this.prisma.savedPlace.create({
      data: {
        circleId,
        createdById: userId,
        name: dto.name,
        type: dto.type,
        latitude: dto.latitude,
        longitude: dto.longitude,
        radiusM: dto.radiusM,
        notifyArrive: dto.notifyArrive ?? true,
        notifyLeave: dto.notifyLeave ?? true,
        notifyUserIds: await this.validRecipients(circleId, dto.notifyUserIds),
      },
    });
    return this.view(p);
  }

  private async editable(placeId: string, userId: string) {
    const p = await this.prisma.savedPlace.findUnique({ where: { id: placeId } });
    if (!p) throw notFound('Place');
    const m = await this.access.requireMember(p.circleId, userId);
    if (p.createdById !== userId && m.role === 'MEMBER') throw notFound('Place');
    return p;
  }

  async update(placeId: string, userId: string, dto: UpdatePlaceDto) {
    const p = await this.editable(placeId, userId);
    const moved = dto.latitude !== undefined || dto.longitude !== undefined || dto.radiusM !== undefined;
    const updated = await this.prisma.$transaction(async (tx) => {
      // A moved fence starts fresh, so nobody gets an alert caused by the edit itself.
      if (moved) await tx.geofenceState.deleteMany({ where: { placeId } });
      return tx.savedPlace.update({
        where: { id: placeId },
        data: {
          ...dto,
          notifyUserIds: dto.notifyUserIds ? await this.validRecipients(p.circleId, dto.notifyUserIds) : undefined,
        },
      });
    });
    return this.view(updated);
  }

  async remove(placeId: string, userId: string) {
    await this.editable(placeId, userId);
    await this.prisma.savedPlace.delete({ where: { id: placeId } });
  }

  /**
   * Runs accepted samples through every fence in the circles the user is sharing with.
   * Arrival/departure alerts would reveal location, so they only exist while sharing is on,
   * and they only reach members who are allowed to see this person right now.
   */
  async processSamples(userId: string, sharingCircleIds: string[], samples: FenceSample[]) {
    if (sharingCircleIds.length === 0 || samples.length === 0) return;
    const places = await this.prisma.savedPlace.findMany({ where: { circleId: { in: sharingCircleIds } } });
    if (places.length === 0) return;
    const states = await this.prisma.geofenceState.findMany({ where: { userId, placeId: { in: places.map((p) => p.id) } } });
    const stateBy = new Map(states.map((s) => [s.placeId, s]));

    for (const place of places) {
      const lastEvent = await this.prisma.geofenceEvent.findFirst({ where: { userId, placeId: place.id }, orderBy: { occurredAt: 'desc' } });
      const st = stateBy.get(place.id);
      let state = {
        inside: st ? st.inside : null,
        pendingInside: st?.pendingInside ?? null,
        pendingSince: st?.pendingSince ?? null,
        lastEventType: (lastEvent?.type ?? null) as 'ARRIVE' | 'LEAVE' | null,
        lastEventAt: lastEvent?.occurredAt ?? null,
      };
      const events: { type: 'ARRIVE' | 'LEAVE'; at: Date }[] = [];
      for (const s of samples) {
        const d = evaluateGeofence({ centerLat: place.latitude, centerLon: place.longitude, radiusM: place.radiusM }, state, s);
        state = { ...state, ...d.next };
        if (d.event) {
          events.push({ type: d.event, at: s.at });
          state.lastEventType = d.event;
          state.lastEventAt = s.at;
        }
      }
      if (state.inside === null) continue;
      await this.prisma.geofenceState.upsert({
        where: { userId_placeId: { userId, placeId: place.id } },
        create: { userId, placeId: place.id, inside: state.inside, pendingInside: state.pendingInside, pendingSince: state.pendingSince },
        update: { inside: state.inside, pendingInside: state.pendingInside, pendingSince: state.pendingSince, ...(events.length ? { changedAt: new Date() } : {}) },
      });
      for (const e of events) await this.emit(userId, place, e.type, e.at);
    }
  }

  private async emit(userId: string, place: SavedPlace, type: 'ARRIVE' | 'LEAVE', at: Date) {
    await this.prisma.geofenceEvent.create({ data: { userId, placeId: place.id, type, occurredAt: at } });
    if ((type === 'ARRIVE' && !place.notifyArrive) || (type === 'LEAVE' && !place.notifyLeave)) return;
    const viewers = new Set((await this.access.locationViewers(userId)).get(place.circleId) ?? []);
    const muted = new Set(
      (await this.prisma.circleMembership.findMany({ where: { circleId: place.circleId, muted: true }, select: { userId: true } })).map((m) => m.userId),
    );
    const chosen = place.notifyUserIds.length ? new Set(place.notifyUserIds) : null;
    const recipients = [...viewers].filter((id) => !muted.has(id) && (!chosen || chosen.has(id)));
    const profile = await this.prisma.userProfile.findUnique({ where: { userId } });
    const name = profile?.displayName ?? 'A member';
    const verb = type === 'ARRIVE' ? 'arrived at' : 'left';
    for (const r of recipients) {
      await this.notifications.notify(r, 'ARRIVAL', `${name} ${verb} ${place.name}`, '', { circleId: place.circleId, placeId: place.id, userId, type });
    }
    this.bus.publish({
      recipients: [...viewers],
      circleId: place.circleId,
      type: 'geofence.event',
      data: { circleId: place.circleId, placeId: place.id, placeName: place.name, userId, type, at },
    });
    await this.feed.post(place.circleId, userId, type, { placeId: place.id, placeName: place.name });
  }
}
