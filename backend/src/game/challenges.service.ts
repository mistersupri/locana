import { Injectable } from '@nestjs/common';
import { CircleAccessService } from '../circles/circle-access.service';
import { FeedService } from '../circles/feed.service';
import { badRequest } from '../common/errors';
import { PrismaService, Tx } from '../prisma/prisma.service';
import { GameEffects } from './effects';

export type ChallengeMetric = 'TILES' | 'LANDMARKS' | 'QUESTS';

/** Cooperative goals a circle sets for itself. Progress is the sum of members' validated activity. */
@Injectable()
export class ChallengesService {
  constructor(private readonly prisma: PrismaService, private readonly access: CircleAccessService, private readonly feed: FeedService) {}

  async create(circleId: string, userId: string, dto: { title: string; metric: ChallengeMetric; target: number; days: number }) {
    await this.access.requireMember(circleId, userId);
    const active = await this.prisma.circleChallenge.count({ where: { circleId, completedAt: null, endsAt: { gt: new Date() } } });
    if (active >= 5) throw badRequest('TOO_MANY_CHALLENGES', 'Finish or wait out a challenge before starting another');
    const c = await this.prisma.circleChallenge.create({
      data: { circleId, createdById: userId, title: dto.title, metric: dto.metric, target: dto.target, endsAt: new Date(Date.now() + dto.days * 86_400_000) },
    });
    await this.feed.post(circleId, userId, 'CHALLENGE', { challengeId: c.id, title: c.title, state: 'STARTED' });
    return this.view(c);
  }

  view(c: { id: string; circleId: string; title: string; metric: string; target: number; progress: number; startsAt: Date; endsAt: Date; completedAt: Date | null }) {
    const now = new Date();
    return { ...c, status: c.completedAt ? 'COMPLETED' : c.endsAt <= now ? 'ENDED' : 'ACTIVE' };
  }

  async list(circleId: string, userId: string) {
    await this.access.requireMember(circleId, userId);
    const rows = await this.prisma.circleChallenge.findMany({ where: { circleId }, orderBy: { startsAt: 'desc' }, take: 30 });
    return rows.map((r) => this.view(r));
  }

  async bump(tx: Tx, userId: string, metric: ChallengeMetric, amount: number, fx: GameEffects) {
    const now = new Date();
    const circles = await tx.circleMembership.findMany({ where: { userId }, select: { circleId: true } });
    if (circles.length === 0) return;
    const open = await tx.circleChallenge.findMany({
      where: { circleId: { in: circles.map((c) => c.circleId) }, metric, completedAt: null, startsAt: { lte: now }, endsAt: { gt: now } },
    });
    for (const c of open) {
      const updated = await tx.circleChallenge.update({ where: { id: c.id }, data: { progress: { increment: amount } } });
      if (updated.progress >= updated.target) {
        const done = await tx.circleChallenge.updateMany({ where: { id: c.id, completedAt: null }, data: { completedAt: now, progress: updated.target } });
        if (done.count) {
          fx.challengeCompletions.push({ circleId: c.circleId, title: c.title });
          await this.feed.post(c.circleId, userId, 'CHALLENGE', { challengeId: c.id, title: c.title, state: 'COMPLETED' }, tx);
        }
      }
    }
  }
}
