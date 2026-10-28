import { Inject, Injectable, Logger } from '@nestjs/common';
import nodemailer, { Transporter } from 'nodemailer';
import { APP_ENV, AppEnv } from '../config/env';

/**
 * Outgoing e-mail for password resets. With no SMTP_URL in development the message is logged
 * so the flow can be tested locally. In production a missing SMTP_URL is a startup error.
 */
@Injectable()
export class MailService {
  private readonly log = new Logger('Mail');
  private readonly transport: Transporter | null;

  constructor(@Inject(APP_ENV) private readonly env: AppEnv) {
    if (env.smtpUrl) this.transport = nodemailer.createTransport(env.smtpUrl);
    else if (env.nodeEnv === 'production') throw new Error('SMTP_URL is required in production for password resets');
    else this.transport = null;
  }

  async send(to: string, subject: string, text: string) {
    if (!this.transport) {
      this.log.warn(`[dev mail, not sent] to=${to} subject="${subject}"\n${text}`);
      return;
    }
    await this.transport.sendMail({ from: this.env.mailFrom, to, subject, text });
  }
}
