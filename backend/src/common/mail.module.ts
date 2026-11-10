import { Module } from '@nestjs/common';
import { MailService } from '../auth/mail.service';

/** Outgoing e-mail, shared by password resets and SOS messages. */
@Module({ providers: [MailService], exports: [MailService] })
export class MailModule {}
