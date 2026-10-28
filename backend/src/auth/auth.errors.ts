import { HttpStatus } from '@nestjs/common';
import { ApiError } from '../common/errors';

const messages: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email or password is incorrect',
  INVALID_REFRESH_TOKEN: 'Your session has ended, please sign in again',
  REFRESH_TOKEN_EXPIRED: 'Your session has expired, please sign in again',
  INVALID_ACCESS_TOKEN: 'Sign in to continue',
  SESSION_REVOKED: 'This session was signed out',
  MISSING_TOKEN: 'Sign in to continue',
};

export const unauthenticated = (code: keyof typeof messages | string) =>
  new ApiError(HttpStatus.UNAUTHORIZED, code, messages[code] ?? 'Sign in to continue');
