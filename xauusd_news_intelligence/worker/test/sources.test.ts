import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { fetchNewsCandidates } from '../src/sources/news.ts';
import { fetchSpotHistory } from '../src/sources/globalSpot.ts';

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('source adapters', () => {
  it('normalizes GDELT articles and isolates a failed official source', async () => {
    const now = Date.parse('2026-10-26T12:00:00Z');
    const fetcher = async (input: RequestInfo | URL): Promise<Response> => {
      const url = String(input);
      if (url.includes('gdeltproject')) return response({ articles: [{ url: 'https://example.com/a', title: 'Fed rate decision', seendate: '20261026T115000Z', domain: 'example.com', sourcecountry: 'United States', language: 'English' }] });
      return new Response('down', { status: 503 });
    };
    const result = await fetchNewsCandidates({}, now, fetcher);
    assert.equal(result.items.length, 1);
    assert.equal(result.items[0]?.title, 'Fed rate decision');
    assert.equal(result.sourceStatus.some(s => s.ok === false), true);
  });
  it('does not replace a missing publication timestamp with the current time', async () => {
    const now = Date.parse('2026-10-26T12:00:00Z');
    const fetcher = async (input: RequestInfo | URL): Promise<Response> => String(input).includes('gdeltproject')
      ? response({ articles: [{ url: 'https://example.com/no-date', title: 'Fed statement', domain: 'example.com' }] })
      : response({});
    const result = await fetchNewsCandidates({}, now, fetcher);
    assert.equal(result.items[0]?.publishedAt, '');
  });
  it('rejects malformed or stale intraday responses', async () => {
    const now = Date.parse('2026-10-26T12:00:00Z');
    const staleFetcher = async () => response({ updated_at: '2026-10-26T10:00:00Z', data_state: { status: 'stale' }, points: [] });
    assert.deepEqual((await fetchSpotHistory(now, staleFetcher)).available, false);
  });
  it('accepts fresh intraday history with enough points', async () => {
    const now = Date.parse('2026-10-26T12:00:00Z');
    const points = Array.from({ length: 300 }, (_, i) => ({ t: Math.floor((now - (299 - i) * 5 * 60_000) / 1000), p: 4000 + i * 0.1 }));
    const fetcher = async (input: RequestInfo | URL) => String(input).includes('/spot?')
      ? response({ spot_usd_oz: 4012.5, updated_at: new Date(now).toISOString(), price_as_of: new Date(now).toISOString(), data_state: { status: 'fresh', age_seconds: 0 } })
      : response({ updated_at: new Date(now).toISOString(), data_state: { status: 'fresh' }, points, coverage_seconds: 299 * 300 });
    const result = await fetchSpotHistory(now, fetcher);
    assert.equal(result.available, true);
    if (result.available) { assert.equal(result.observations.length, 300); assert.equal(result.currentPrice, 4012.5); }
  });
});
