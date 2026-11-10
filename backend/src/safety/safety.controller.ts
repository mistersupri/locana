import { Body, Controller, Get, HttpCode, Param, ParseUUIDPipe, Post, Put } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/auth-context';
import { RateLimit } from '../common/rate-limit';
import { QuickMessageDto, SetEmergencyContactsDto, SosDto } from './safety.dto';
import { SafetyService } from './safety.service';

const uuid = new ParseUUIDPipe({ version: '4' });

@ApiTags('safety')
@ApiBearerAuth()
@Controller()
export class SafetyController {
  constructor(private readonly safety: SafetyService) {}

  @Get('emergency-contacts')
  contacts(@CurrentUser() u: AuthUser) {
    return this.safety.contacts(u.id);
  }

  @Put('emergency-contacts')
  setContacts(@CurrentUser() u: AuthUser, @Body() dto: SetEmergencyContactsDto) {
    return this.safety.setContacts(u.id, dto.contacts);
  }

  /** Held-button SOS or an unanswered crash alert. Rate limited so a stuck button cannot flood a circle. */
  @Post('sos')
  @HttpCode(200)
  @RateLimit({ name: 'sos', limit: 5, windowSec: 600 })
  sos(@CurrentUser() u: AuthUser, @Body() dto: SosDto) {
    return this.safety.sos(u.id, dto);
  }

  @Get('quick-messages')
  catalog() {
    return this.safety.catalog();
  }

  @Post('circles/:circleId/members/:userId/quick-message')
  @HttpCode(200)
  quick(@CurrentUser() u: AuthUser, @Param('circleId', uuid) circleId: string, @Param('userId', uuid) userId: string, @Body() dto: QuickMessageDto) {
    return this.safety.sendQuickMessage(circleId, u.id, userId, dto);
  }
}
