/**
 * Geographic helpers shared by location validation, geofences and the exploration grid.
 *
 * Exploration grid: Web-Mercator "slippy map" tiles. A tile id is "z/x/y". At the default
 * zoom 17 a tile is ~305 m wide at the equator (~300 m across Indonesia) and shrinks with
 * cos(latitude) further north/south. The same formula is implemented in the Android
 * domain module (TileGrid.kt), so both sides agree on tile ids.
 */
export const EARTH_RADIUS_M = 6_371_008.8;
export const DEFAULT_TILE_ZOOM = 17;

const toRad = (d: number) => (d * Math.PI) / 180;
const toDeg = (r: number) => (r * 180) / Math.PI;

export function haversineM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
}

export function isValidCoordinate(lat: number, lon: number): boolean {
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= -85.05112878 && lat <= 85.05112878 && lon >= -180 && lon <= 180;
}

export interface TileId {
  z: number;
  x: number;
  y: number;
}

export function tileFor(lat: number, lon: number, z = DEFAULT_TILE_ZOOM): TileId {
  const n = 2 ** z;
  const x = Math.min(n - 1, Math.max(0, Math.floor(((lon + 180) / 360) * n)));
  const latRad = toRad(lat);
  const y = Math.min(
    n - 1,
    Math.max(0, Math.floor(((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2) * n)),
  );
  return { z, x, y };
}

export const tileKey = (t: TileId) => `${t.z}/${t.x}/${t.y}`;

export function parseTileKey(key: string): TileId | null {
  const m = /^(\d{1,2})\/(\d+)\/(\d+)$/.exec(key);
  if (!m) return null;
  const t = { z: Number(m[1]), x: Number(m[2]), y: Number(m[3]) };
  const n = 2 ** t.z;
  return t.x < n && t.y < n ? t : null;
}

/** North-west corner of a tile. */
export function tileOrigin(t: TileId): { lat: number; lon: number } {
  const n = 2 ** t.z;
  const lon = (t.x / n) * 360 - 180;
  const lat = toDeg(Math.atan(Math.sinh(Math.PI * (1 - (2 * t.y) / n))));
  return { lat, lon };
}

export function tileBounds(t: TileId) {
  const nw = tileOrigin(t);
  const se = tileOrigin({ z: t.z, x: t.x + 1, y: t.y + 1 });
  return { north: nw.lat, west: nw.lon, south: se.lat, east: se.lon };
}

/** Rough bounding box in degrees around a point, for indexed pre-filtering before haversine. */
export function boundingBox(lat: number, lon: number, radiusM: number) {
  const dLat = toDeg(radiusM / EARTH_RADIUS_M);
  const dLon = toDeg(radiusM / (EARTH_RADIUS_M * Math.max(0.01, Math.cos(toRad(lat)))));
  return { minLat: lat - dLat, maxLat: lat + dLat, minLon: lon - dLon, maxLon: lon + dLon };
}
