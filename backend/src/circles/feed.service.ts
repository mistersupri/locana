import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { badRequest, notFound } from '../common/errors';
import { PrismaService, Tx } from '../prisma/prisma.service';
import { CircleAccessService } from './circle-access.service';

export type FeedKind = 'ACHIEVEMENT' | 'LEVEL_UP' | 'QUEST' | 'JOINED' | 'LEFT' | 'TERRITORY' | 'CHALLENGE' | 'ARRIVE' | 'LEAVE';

export const REACTIONS = ['clap', 'fire', 'heart', 'wow', 'laugh'] as const;

/** Circle activity feed. Payloads hold names and codes for display; never coordinates. */
@Injectable()
export class FeedService {
  constructor(private readonly prisma: PrismaService, private readonly access: CircleAccessService) {}

  async post(circleId: string, actorId: string, kind: FeedKind, payload: Record<string, unknown>, tx: Tx = this.prisma) {
    return tx.activityFeedItem.create({ data: { circleId, actorId, kind, payload: payload as Prisma.InputJsonValue } });
  }

  /** Posts to every circle of the actor (achievements, level ups). */
  async postToAllCircles(actorId: string, kind: FeedKind, payload: Record<string, unknown>, tx: Tx = this.prisma) {
    const circles = await tx.circleMembership.findMany({ where: { userId: actorId }, select: { circleId: true } });
    if (circles.length === 0) return;
    await tx.activityFeedItem.createMany({
      data: circles.map((c) => ({ circleId: c.circleId, actorId, kind, payload: payload as Prisma.InputJsonValue })),
    });
  }

  async list(circleId: string, viewerId: string, cursor?: string, limit = 25) {
    await this.access.requireMember(circleId, viewerId);
    const blocked = await this.prisma.userBlock.findMany({ where: { blockerId: viewerId }, select: { blockedId: true } });
    const take = Math.min(Math.max(limit, 1), 50);
    const rows = await this.prisma.activityFeedItem.findMany({
      where: {
        circleId,
        actorId: { notIn: blocked.map((b) => b.blockedId) },
        ...(cursor ? { createdAt: { lt: new Date(cursor) } } : {}),
      },
      orderBy: { createdAt: 'desc' },
      take: take + 1,
      include: { actor: { select: { id: true, username: true, profile: { select: { displayName: true, avatarKey: true } } } }, reactions: true },
    });
    const page = rows.slice(0, take);
    return {
      items: page.map((r) => ({
        id: r.id,
        kind: r.kind,
        payload: r.payload,
        createdAt: r.createdAt,
        actor: { id: r.actor.id, username: r.actor.username, displayName: r.actor.profile?.displayName ?? r.actor.username, avatarKey: r.actor.profile?.avatarKey },
        reactions: REACTIONS.map((emoji) => ({ emoji, count: r.reactions.filter((x) => x.emoji === emoji).length })).filter((x) => x.count > 0),
        myReaction: r.reactions.find((x) => x.userId === viewerId)?.emoji ?? null,
      })),
      nextCursor: rows.length > take ? page[page.length - 1].createdAt.toISOString() : null,
    };
  }

  async react(itemId: string, userId: string, emoji: string | null) {
    const item = await this.prisma.activityFeedItem.findUnique({ where: { id: itemId } });
    if (!item) throw notFound('Feed item');
    await this.access.requireMember(item.circleId, userId);
    if (emoji === null) {
      await this.prisma.feedReaction.deleteMany({ where: { feedItemId: itemId, userId } });
      return;
    }
    if (!(REACTIONS as readonly string[]).includes(emoji)) throw badRequest('INVALID_REACTION', 'Unknown reaction');
    await this.prisma.feedReaction.upsert({
      where: { feedItemId_userId: { feedItemId: itemId, userId } },
      create: { feedItemId: itemId, userId, emoji },
      update: { emoji },
    });
  }
}
