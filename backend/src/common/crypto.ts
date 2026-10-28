import { createHash, randomBytes, randomInt, timingSafeEqual } from 'crypto';

export const sha256 = (v: string) => createHash('sha256').update(v).digest('hex');

/** URL-safe opaque token (refresh tokens). 32 bytes = 256 bits. */
export const opaqueToken = (bytes = 32) => randomBytes(bytes).toString('base64url');

// Crockford-style alphabet without 0/O/1/I/L so codes survive being read aloud or retyped.
const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

/** Human-typable random code (invitation and reset codes). 10 chars = ~49 bits. */
export function humanCode(length = 10): string {
  let out = '';
  for (let i = 0; i < length; i++) out += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  return out;
}

export const normalizeCode = (code: string) => code.toUpperCase().replace(/[^0-9A-Z]/g, '');

export function safeEqual(a: string, b: string): boolean {
  const ba = Buffer.from(a);
  const bb = Buffer.from(b);
  return ba.length === bb.length && timingSafeEqual(ba, bb);
}
