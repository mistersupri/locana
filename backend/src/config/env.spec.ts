import { assertSmtpUrl, loadEnv } from './env';

describe('SMTP_URL validation', () => {
  it('accepts empty (mail is logged in development) and real smtp URLs', () => {
    expect(() => assertSmtpUrl(undefined)).not.toThrow();
    expect(() => assertSmtpUrl('')).not.toThrow();
    expect(() => assertSmtpUrl('smtps://me%40gmail.com:app-password@smtp.gmail.com:465')).not.toThrow();
    expect(() => assertSmtpUrl('SMTP://user:pw@localhost:1025')).not.toThrow();
  });

  it('rejects a bare host name with an actionable message', () => {
    expect(() => assertSmtpUrl('smtp.gmail.com')).toThrow(/must be a full URL starting with smtps:\/\//);
  });

  it('never puts the value, which may hold a password, in the message', () => {
    let message = '';
    try {
      assertSmtpUrl('me@gmail.com:hunter2-secret@smtp.gmail.com');
    } catch (e) {
      message = (e as Error).message;
    }
    expect(message).not.toBe('');
    expect(message).not.toContain('hunter2-secret');
  });

  it('is enforced when the environment loads', () => {
    const keep = { ...process.env };
    process.env.JWT_ACCESS_SECRET = 'x'.repeat(40);
    process.env.DATABASE_URL = 'postgresql://u:p@localhost:5432/db';
    process.env.SMTP_URL = 'smtp.gmail.com';
    try {
      expect(() => loadEnv()).toThrow(/SMTP_URL/);
      process.env.SMTP_URL = 'smtps://u:p@smtp.gmail.com:465';
      expect(loadEnv().smtpUrl).toBe('smtps://u:p@smtp.gmail.com:465');
    } finally {
      process.env = keep;
    }
  });
});
