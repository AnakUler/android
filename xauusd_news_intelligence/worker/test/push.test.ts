import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { dispatchNewAlerts } from '../src/push/dispatch.ts';
import type { NewsItem } from '../src/domain/types.ts';

class MemoryKV {
  values = new Map<string, string>();
  async get(key: string) { return this.values.get(key) ?? null; }
  async put(key: string, value: string) { this.values.set(key, value); }
  async delete(key: string) { this.values.delete(key); }
  async list({ prefix = '' } = {}) { return { keys: [...this.values.keys()].filter(name => name.startsWith(prefix)).map(name => ({ name })), list_complete: true }; }
}
const item = (level: 'NORMAL' | 'MEDIUM' | 'HIGH', key = 'k1'): NewsItem => ({ key, title: 'Fed news', source: 'test', url: 'https://example.com', publishedAt: '2026-10-26T10:00:00Z', detectedAt: '2026-10-26T10:01:00Z', summary: '', level, reason: 'test reason', bullish: 'Jika yield turun, emas dapat naik.', bearish: 'Jika dolar menguat, emas dapat turun.', tags: [] });

describe('dispatchNewAlerts', () => {
  it('never sends NORMAL and sends a new MEDIUM item once', async () => {
    const kv = new MemoryKV();
    kv.values.set('device:tokens', JSON.stringify([{ id: 'one', token: 'fcm-token-12345678901234567890' }]));
    const calls: unknown[] = [];
    const env = { DATA: kv, send: async (_token: string, payload: unknown) => { calls.push(payload); return { ok: true }; } };
    const first = await dispatchNewAlerts(env, [item('NORMAL', 'normal'), item('MEDIUM', 'medium')]);
    const second = await dispatchNewAlerts(env, [item('MEDIUM', 'medium')]);
    assert.equal(calls.length, 1);
    assert.equal(first.sent, 1);
    assert.equal(second.sent, 0);
  });
  it('includes conditional bullish/bearish reasoning and upper/lower price zones in push', async () => {
    const kv = new MemoryKV(); kv.values.set('device:tokens', JSON.stringify([{ id: 'one', token: 'fcm-token-12345678901234567890' }]));
    let body = '';
    const env = { DATA: kv, send: async (_token: string, payload: unknown) => { body = (payload as { body: string }).body; return { ok: true }; } };
    const zone = { available: true as const, currentPrice: 4000, lower: 3950.25, upper: 4050.75, horizonHours: 4 as const, sampleCount: 100, coverageHours: 24, lowerReturnPct: -1.2, upperReturnPct: 1.3, method: 'rolling_4h_empirical_q10_q90' as const, uncertainty: 'Rentang historis indikatif.' };
    await dispatchNewAlerts(env, [{ ...item('HIGH'), scenario: zone }]);
    assert.match(body, /Jika yield turun/);
    assert.match(body, /Jika dolar menguat/);
    assert.match(body, /3950\.25/);
    assert.match(body, /4050\.75/);
  });
  it('ignores malformed short device tokens instead of attempting delivery', async () => {
    const kv = new MemoryKV(); kv.values.set('device:tokens', JSON.stringify([{ id: 'bad', token: 'too-short' }]));
    let called = false;
    const result = await dispatchNewAlerts({ DATA: kv, send: async () => { called = true; return { ok: true }; } }, [item('HIGH')]);
    assert.equal(called, false);
    assert.equal(result.skippedNoDevice, 1);
  });
  it('does not mark an alert as sent when push delivery fails', async () => {
    const kv = new MemoryKV(); kv.values.set('device:tokens', JSON.stringify([{ id: 'one', token: 'fcm-token-12345678901234567890' }]));
    const env = { DATA: kv, send: async () => ({ ok: false, error: 'test failure' }) };
    const result = await dispatchNewAlerts(env, [item('HIGH')]);
    assert.equal(result.failed, 1);
    assert.equal(kv.values.has('alerts:sent'), false);
  });
});
