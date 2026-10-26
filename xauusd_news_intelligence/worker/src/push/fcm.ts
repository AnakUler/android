export interface FcmEnvironment { FCM_PROJECT_ID?: string; FCM_CLIENT_EMAIL?: string; FCM_PRIVATE_KEY?: string; }
export interface PushPayload { title: string; body: string; data: Record<string, string>; }
export interface DeliveryResult { ok: boolean; error?: string; }
let cachedToken: string | null = null;
let cachedTokenExpiresAt = 0;
const encoder = new TextEncoder();
function base64Url(bytes: Uint8Array): string {
  let binary = ''; for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
function encodeJson(value: unknown): string { return base64Url(encoder.encode(JSON.stringify(value))); }
function truncateUtf8(value: string, maxBytes: number): string {
  let result = ''; let used = 0;
  for (const character of value) {
    const size = encoder.encode(character).length;
    if (used + size > maxBytes) break;
    result += character; used += size;
  }
  return result;
}
function pemToBytes(pem: string): Uint8Array {
  const content = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, '');
  const binary = atob(content); return Uint8Array.from(binary, c => c.charCodeAt(0));
}
async function getAccessToken(env: FcmEnvironment): Promise<string> {
  if (cachedToken && Date.now() < cachedTokenExpiresAt - 60_000) return cachedToken;
  if (!env.FCM_CLIENT_EMAIL || !env.FCM_PRIVATE_KEY) throw new Error('FCM server credentials are not configured');
  const now = Math.floor(Date.now() / 1000);
  const unsigned = `${encodeJson({ alg: 'RS256', typ: 'JWT' })}.${encodeJson({ iss: env.FCM_CLIENT_EMAIL, scope: 'https://www.googleapis.com/auth/firebase.messaging', aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600 })}`;
  const key = await crypto.subtle.importKey('pkcs8', pemToBytes(env.FCM_PRIVATE_KEY.replace(/\\n/g, '\n')), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, encoder.encode(unsigned));
  const assertion = `${unsigned}.${base64Url(new Uint8Array(signature))}`;
  const response = await fetch('https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }) });
  if (!response.ok) throw new Error(`Google OAuth returned HTTP ${response.status}`);
  const data = await response.json() as { access_token?: string; expires_in?: number };
  if (!data.access_token) throw new Error('Google OAuth response missing access token');
  cachedToken = data.access_token; cachedTokenExpiresAt = Date.now() + Math.min(3500, Number(data.expires_in) || 3500) * 1000;
  return cachedToken;
}
export async function sendFcmNotification(env: FcmEnvironment, deviceToken: string, payload: PushPayload): Promise<DeliveryResult> {
  if (!env.FCM_PROJECT_ID || !env.FCM_CLIENT_EMAIL || !env.FCM_PRIVATE_KEY) return { ok: false, error: 'FCM credentials are not configured' };
  try {
    const accessToken = await getAccessToken(env);
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(env.FCM_PROJECT_ID)}/messages:send`, {
      method: 'POST', headers: { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
      body: JSON.stringify({ message: { token: deviceToken, notification: { title: truncateUtf8(payload.title, 240), body: truncateUtf8(payload.body, 2400) }, data: Object.fromEntries(Object.entries(payload.data).map(([key, value]) => [key, truncateUtf8(value, key === 'url' ? 800 : key === 'key' ? 200 : 240)])), android: { priority: 'HIGH', notification: { channel_id: 'xauusd_news_alerts', sound: 'default' } } } }),
    });
    if (response.ok) return { ok: true };
    // Never return the FCM body: it can contain device-token fragments or provider internals.
    return { ok: false, error: `FCM HTTP ${response.status}` };
  } catch (error) {
    return { ok: false, error: error instanceof Error ? error.message.slice(0, 120) : 'FCM delivery error' };
  }
}
