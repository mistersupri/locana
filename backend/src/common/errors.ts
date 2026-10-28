import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { Response } from 'express';

/**
 * Every error leaves the API as { error: { code, message, details? } }.
 * Codes are stable strings the Android client switches on; messages are for humans.
 */
export class ApiError extends HttpException {
  constructor(status: number, public readonly code: string, message: string, public readonly details?: unknown) {
    super({ code, message, details }, status);
  }
}

export const notFound = (what: string) => new ApiError(HttpStatus.NOT_FOUND, `${what.toUpperCase()}_NOT_FOUND`, `${what} not found`);
export const forbidden = (code = 'FORBIDDEN', message = 'You do not have access to this resource') =>
  new ApiError(HttpStatus.FORBIDDEN, code, message);
export const badRequest = (code: string, message: string, details?: unknown) => new ApiError(HttpStatus.BAD_REQUEST, code, message, details);
export const conflict = (code: string, message: string) => new ApiError(HttpStatus.CONFLICT, code, message);

@Catch()
export class ApiExceptionFilter implements ExceptionFilter {
  private readonly log = new Logger('Http');

  catch(exception: unknown, host: ArgumentsHost) {
    const res = host.switchToHttp().getResponse<Response>();
    if (!res || typeof res.status !== 'function') return; // non-HTTP context
    const { status, body } = this.toBody(exception);
    if (status >= 500) this.log.error(exception instanceof Error ? exception.stack : String(exception));
    res.status(status).json({ error: body });
  }

  private toBody(e: unknown): { status: number; body: { code: string; message: string; details?: unknown } } {
    if (e instanceof ApiError) {
      const r = e.getResponse() as { code: string; message: string; details?: unknown };
      return { status: e.getStatus(), body: r };
    }
    if (e instanceof HttpException) {
      const status = e.getStatus();
      const r = e.getResponse();
      const message = typeof r === 'string' ? r : ((r as { message?: string | string[] }).message ?? e.message);
      const code = status === 400 ? 'VALIDATION_FAILED' : status === 401 ? 'UNAUTHENTICATED' : status === 404 ? 'NOT_FOUND' : `HTTP_${status}`;
      return Array.isArray(message)
        ? { status, body: { code, message: 'Request validation failed', details: message } }
        : { status, body: { code, message: String(message) } };
    }
    if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2002') {
      return { status: 409, body: { code: 'ALREADY_EXISTS', message: 'A record with these values already exists' } };
    }
    if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2025') {
      return { status: 404, body: { code: 'NOT_FOUND', message: 'Record not found' } };
    }
    return { status: 500, body: { code: 'INTERNAL', message: 'Something went wrong on our side' } };
  }
}
