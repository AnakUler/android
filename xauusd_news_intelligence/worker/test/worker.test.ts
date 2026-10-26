import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { fetchHandler } from '../src/index.ts';
class MemoryKV {
  values = new Map<string, string>();
  async get(key: string) { return this.values.get(key) ?? null; }
  async put(key: string, value: string) { this.values.set(key, value); }
  async delete(key: string) { this.values.delete(key); }
  async list({ prefix = '', limit = 100 } = {}) { return { keys: [...this.values.keys()].filter(k => k.startsWith(prefix)).slice(0, limit).map(name => ({ name })), list_complete: true }; }
}
const env = () => ({ DATA: new MemoryKV(), DEVICE_REGISTRATION_CODE: 'test-registration-code' });

describe('Worker HTTP API', () => {
  it('returns health and cached news without crashing when cache is empty', async () => {
    const e = env();
    const health = await fetchHandler(new Request('https://worker.test/api/health'), e);
    assert.equal(health.status, 200);
    const news = await fetchHandler(new Request('https://worker.test/api/news'), e);
    assert.equal(news.status, 200);
    const payload = await news.json() as { items: unknown[]; calendarEvents: unknown[] };
    assert.equal(payload.items.length, 0);
    assert.equal(payload.calendarEvents.length, 0);
  });
  it('rejects oversized registration payloads before parsing JSON', async () => {
    const e = env();
    const oversized = await fetchHandler(new Request('https://worker.test/api/register-device', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ token: 'x'.repeat(9000), code: 'test-registration-code' }) }), e);
    assert.equal(oversized.status, 413);
  });
  it('rejects device registration with a wrong code and accepts a valid token', async () => {
    const e = env();
    const wrong = await fetchHandler(new Request('https://worker.test/api/register-device', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ token: 'fcm-token-12345678901234567890', code: 'wrong' }) }), e);
    assert.equal(wrong.status, 403);
    const good = await fetchHandler(new Request('https://worker.test/api/register-device', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ token: 'fcm-token-12345678901234567890', code: 'test-registration-code' }) }), e);
    assert.equal(good.status, 200);
    assert.equal(JSON.parse(e.DATA.values.get('device:tokens') || '[]').length, 1);
    const removed = await fetchHandler(new Request('https://worker.test/api/unregister-device', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ token: 'fcm-token-12345678901234567890', code: 'test-registration-code' }) }), e);
    assert.equal(removed.status, 200);
    assert.equal(JSON.parse(e.DATA.values.get('device:tokens') || '[]').length, 0);
  });
});
