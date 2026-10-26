import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { sendFcmNotification } from '../src/push/fcm.ts';

const payload = { title: '[HIGH] CPI', body: 'Jika yield turun, emas dapat naik. Jika dolar menguat, emas dapat turun. Zona historis 4 jam: bawah $3900 atas $4100.', data: { key: 'k', level: 'HIGH', url: 'https://example.com', title: 'CPI' } };

describe('FCM v1 transport', () => {
  it('fails safely when server credentials are missing', async () => {
    const result = await sendFcmNotification({}, 'fcm-token-12345678901234567890', payload);
    assert.deepEqual(result, { ok: false, error: 'FCM credentials are not configured' });
  });
  it('returns provider status without exposing response body or device token', async () => {
    const pair = generateKeyPairSync('rsa', { modulusLength: 2048 });
    const privateKey = pair.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
    const originalFetch = globalThis.fetch;
    let calls = 0;
    globalThis.fetch = (async () => {
      calls++;
      if (calls === 1) return new Response(JSON.stringify({ access_token: 'test-access-token', expires_in: 3600 }), { status: 200, headers: { 'content-type': 'application/json' } });
      return new Response('sensitive provider body that must not leak', { status: 401 });
    }) as typeof fetch;
    try {
      const result = await sendFcmNotification({ FCM_PROJECT_ID: 'test-project', FCM_CLIENT_EMAIL: 'service@test-project.iam.gserviceaccount.com', FCM_PRIVATE_KEY: privateKey }, 'fcm-token-12345678901234567890', payload);
      assert.deepEqual(result, { ok: false, error: 'FCM HTTP 401' });
      assert.equal(calls, 2);
      assert.equal(JSON.stringify(result).includes('sensitive'), false);
      assert.equal(JSON.stringify(result).includes('fcm-token'), false);
    } finally { globalThis.fetch = originalFetch; }
  });
});
