import { boundingBox, haversineM, parseTileKey, tileBounds, tileFor, tileKey } from './geo';

describe('geo', () => {
  it('measures Monas to Istiqlal at roughly 650 m', () => {
    const d = haversineM(-6.175392, 106.827153, -6.170166, 106.831375);
    expect(d).toBeGreaterThan(600);
    expect(d).toBeLessThan(800);
  });

  it('puts a point inside the bounds of its own tile', () => {
    const t = tileFor(-6.2, 106.8);
    const b = tileBounds(t);
    expect(-6.2).toBeLessThanOrEqual(b.north);
    expect(-6.2).toBeGreaterThanOrEqual(b.south);
    expect(106.8).toBeGreaterThanOrEqual(b.west);
    expect(106.8).toBeLessThanOrEqual(b.east);
  });

  it('round-trips tile keys and rejects malformed ones', () => {
    const t = tileFor(51.5, -0.12);
    expect(parseTileKey(tileKey(t))).toEqual(t);
    expect(parseTileKey('17/x/1')).toBeNull();
    expect(parseTileKey('2/9/9')).toBeNull();
  });

  it('builds a bounding box that contains the radius', () => {
    const bb = boundingBox(-6.2, 106.8, 1000);
    expect(haversineM(-6.2, 106.8, bb.maxLat, 106.8)).toBeGreaterThanOrEqual(999);
    expect(haversineM(-6.2, 106.8, -6.2, bb.maxLon)).toBeGreaterThanOrEqual(999);
  });
});
