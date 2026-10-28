import argon2 from 'argon2';

// OWASP 2024 baseline for Argon2id: m=19 MiB, t=2, p=1.
const OPTIONS = { type: argon2.argon2id, memoryCost: 19_456, timeCost: 2, parallelism: 1 } as const;

export const hashPassword = (pw: string) => argon2.hash(pw, OPTIONS);

let dummyHash: Promise<string> | null = null;

/** Verifies a password. With no stored hash, still burns comparable time so timing does not reveal accounts. */
export async function verifyPassword(hash: string | null | undefined, pw: string): Promise<boolean> {
  if (!hash) {
    dummyHash ??= argon2.hash('locana-timing-equalizer', OPTIONS);
    await argon2.verify(await dummyHash, pw).catch(() => false);
    return false;
  }
  try {
    return await argon2.verify(hash, pw);
  } catch {
    return false;
  }
}
