import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { classifyNews } from '../src/domain/classifier.ts';
import { calculateScenarioZone } from '../src/domain/scenario.ts';
import { makeDeduplicationKey } from '../src/domain/dedupe.ts';
import type { NormalizedNews, SpotObservation } from '../src/domain/types.js';

const news = (title: string, extra: Partial<NormalizedNews> = {}): NormalizedNews => ({
  title, summary: '', source: 'Test Feed', url: 'https://example.com/article', publishedAt: '2026-10-26T10:00:00.000Z', detectedAt: '2026-10-26T10:01:00.000Z', ...extra
});

describe('classifyNews', () => {
  it('marks major central-bank and inflation events HIGH with conditional scenarios', () => {
    const result = classifyNews(news('Federal Reserve announces surprise emergency rate decision'));
    assert.equal(result.level, 'HIGH');
    assert.match(result.bullish, /jika/i);
    assert.match(result.bearish, /jika/i);
    assert.ok(result.reason.length > 8);
  });
  it('marks potentially relevant policy/geopolitical stories MEDIUM', () => {
    assert.equal(classifyNews(news('New US tariff measures announced against imports')).level, 'MEDIUM');
  });
  it('leaves routine unrelated items NORMAL and does not invent a gold effect', () => {
    const result = classifyNews(news('Local football club signs a new goalkeeper'));
    assert.equal(result.level, 'NORMAL');
    assert.match(result.bullish, /belum ada alasan langsung/i);
  });
  it('handles missing optional fields safely', () => {
    assert.equal(classifyNews({ title: '', source: '', url: '', publishedAt: '', detectedAt: '' }).level, 'NORMAL');
  });
});

describe('calculateScenarioZone', () => {
  const makePrices = (now: number): SpotObservation[] => Array.from({ length: 24 * 12 + 1 }, (_, i) => ({ timestampMs: now - (24 * 12 - i) * 5 * 60_000, price: 4000 + Math.sin(i / 3) * 12 + i * 0.02 }));
  it('calculates ordered Q10/Q90 four-hour scenario bounds from adequate recent history', () => {
    const now = Date.parse('2026-10-26T12:00:00Z');
    const result = calculateScenarioZone(makePrices(now), now);
    assert.equal(result.available, true);
    if (result.available) {
      assert.ok(result.lower < result.currentPrice);
      assert.ok(result.upper > result.currentPrice);
      assert.equal(result.horizonHours, 4);
      assert.ok(result.sampleCount >= 20);
    }
  });
  it('anchors the range to the freshest global spot quote when supplied', () => {
    const now = Date.parse('2026-10-26T12:00:00Z');
    const result = calculateScenarioZone(makePrices(now), now, 4100);
    assert.equal(result.available, true);
    if (result.available) assert.equal(result.currentPrice, 4100);
  });
  it('uses timestamps rather than assuming a five-minute sampling cadence', () => {
    const now = Date.parse('2026-10-26T12:00:00Z');
    const twoMinuteSeries = Array.from({ length: 300 }, (_, i) => ({ timestampMs: now - (299 - i) * 2 * 60_000, price: 4000 + Math.sin(i / 8) * 5 + i * 0.02 }));
    const result = calculateScenarioZone(twoMinuteSeries, now);
    assert.equal(result.available, true);
    if (result.available) assert.ok(result.sampleCount >= 20);
  });
  it('refuses stale data instead of manufacturing a price range', () => {
    const now = Date.parse('2026-10-26T12:00:00Z');
    const old = makePrices(now).map(p => ({ ...p, timestampMs: p.timestampMs - 60 * 60_000 }));
    assert.equal(calculateScenarioZone(old, now).available, false); assert.equal(calculateScenarioZone(old, now).reason, 'stale_data');
  });
  it('refuses too few observations and invalid prices', () => {
    const now = Date.now();
    assert.equal(calculateScenarioZone([{ timestampMs: now, price: 4000 }], now).reason, 'insufficient_history');
    assert.equal(calculateScenarioZone([{ timestampMs: now, price: Number.NaN }], now).reason, 'invalid_data');
  });
});

describe('makeDeduplicationKey', () => {
  it('canonicalizes URL tracking params, query and trailing slash', () => {
    const a = news('Fed decision', { url: 'https://Example.com/story/?utm_source=feed#section' });
    const b = news('Fed decision', { url: 'https://example.com/story' });
    assert.equal(makeDeduplicationKey(a), makeDeduplicationKey(b));
  });
  it('canonicalizes a URL-valued source ID rather than preserving tracking parameters', () => {
    const item = news('Fed decision', { id: 'https://example.com/a?utm_source=x', url: 'https://example.com/a?utm_source=x' });
    assert.equal(makeDeduplicationKey(item), 'url:https://example.com/a');
  });
  it('uses a stable source ID when available', () => {
    assert.equal(makeDeduplicationKey(news('one', { id: 'abc' })), makeDeduplicationKey(news('changed title', { id: 'abc' })));
  });
});
