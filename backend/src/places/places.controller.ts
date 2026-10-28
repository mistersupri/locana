import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/auth-context';
import { PlaceDto, UpdatePlaceDto } from './places.dto';
import { PlacesService } from './places.service';

const uuid = new ParseUUIDPipe({ version: '4' });

@ApiTags('places')
@ApiBearerAuth()
@Controller()
export class PlacesController {
  constructor(private readonly places: PlacesService) {}

  @Get('circles/:circleId/places')
  list(@CurrentUser() u: AuthUser, @Param('circleId', uuid) circleId: string) {
    return this.places.list(circleId, u.id);
  }

  @Post('circles/:circleId/places')
  create(@CurrentUser() u: AuthUser, @Param('circleId', uuid) circleId: string, @Body() dto: PlaceDto) {
    return this.places.create(circleId, u.id, dto);
  }

  @Patch('places/:id')
  update(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string, @Body() dto: UpdatePlaceDto) {
    return this.places.update(id, u.id, dto);
  }

  @Delete('places/:id')
  @HttpCode(204)
  remove(@CurrentUser() u: AuthUser, @Param('id', uuid) id: string) {
    return this.places.remove(id, u.id);
  }
}
