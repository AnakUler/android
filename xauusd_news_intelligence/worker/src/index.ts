import { classifyNews } from './domain/classifier.ts';
import { makeDeduplicationKey } from './domain/dedupe.ts';
import { calculateScenarioZone } from './domain/scenario.ts';
import type { NormalizedNews, NewsItem, ScenarioZoneResult } from './domain/types.ts';
import { fetchNewsCandidates, type SourceStatus } from './sources/news.ts';
import { fetchSpotHistory } from './sources/globalSpot.ts';
import { fetchEconomicCalendar, type CalendarEvent } from './sources/economicCalendar.ts';
import { dispatchNewAlerts, type AlertItem, type DispatchSummary } from './push/dispatch.ts';

interface KVKey { name: string; }
interface KVNamespace {
  get(key: string): Promise<string | null>;
  put(key: string, value: string, options?: { expirationTtl?: number }): Promise<void>;
  delete(key: string): Promise<void>;
  list(options?: { prefix?: string; limit?: number; cursor?: string }): Promise<{ keys: KVKey[]; list_complete?: boolean; cursor?: string }>;
}
export interface WorkerEnv {
  DATA: KVNamespace;
  DEVICE_REGISTRATION_CODE?: string;
  FCM_PROJECT_ID?: string;
  FCM_CLIENT_EMAIL?: string;
  FCM_PRIVATE_KEY?: string;
}
interface WorkerContext { waitUntil(promise: Promise<unknown>): void; }
interface StoredFeed { items: AlertItem[]; calendarEvents?: CalendarEvent[]; lastUpdatedAt: string; }
interface RunStatus { lastRunAt: string; sourceStatus: SourceStatus[]; calendar: { ok: boolean; count: number; error?: string }; spot: { available: boolean; reason?: string; currentPrice?: number; asOf?: string }; dispatch: DispatchSummary; warnings: string[]; }
const JSON_HEADERS = { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'access-control-allow-origin': '*', 'access-control-allow-headers': 'content-type', 'access-control-allow-methods': 'GET,POST,DELETE,OPTIONS' };
function json(data: unknown, status = 200): Response { return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS }); }
async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('');
}

type LimitedJsonResult = { ok: true; value: unknown } | { ok: false; status: number; message: string };
async function readLimitedJson(request: Request, maxBytes: number): Promise<LimitedJsonResult> {
  const declaredLength = Number(request.headers.get('content-length') || 0);
  if (declaredLength > maxBytes) return { ok: false, status: 413, message: 'Permintaan terlalu besar.' };
  if (!request.body) return { ok: false, status: 400, message: 'Body JSON tidak tersedia.' };
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const part = await reader.read();
      if (part.done) break;
      total += part.value.byteLength;
      if (total > maxBytes) { await reader.cancel(); return { ok: false, status: 413, message: 'Permintaan terlalu besar.' }; }
      chunks.push(part.value);
    }
    const bytes = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    try { return { ok: true, value: JSON.parse(new TextDecoder().decode(bytes)) as unknown }; }
    catch { return { ok: false, status: 400, message: 'JSON tidak valid.' }; }
  } catch {
    return { ok: false, status: 400, message: 'Body permintaan tidak dapat dibaca.' };
  }
}

function scenarioUnavailable(reason: string): ScenarioZoneResult {
  if (reason.includes('stale')) return { available: false, reason: 'stale_data', message: 'Zona harga belum dapat dihitung karena data harga kedaluwarsa.' };
  if (reason.includes('invalid') || reason.includes('malformed')) return { available: false, reason: 'invalid_data', message: 'Zona harga belum dapat dihitung karena format data tidak valid.' };
  return { available: false, reason: 'insufficient_history', message: 'Zona harga belum dapat dihitung karena riwayat harga tidak cukup.' };
}
function validNews(item: NormalizedNews): boolean {
  return Boolean(item && typeof item.title === 'string' && item.title.trim() && typeof item.url === 'string' && /^https?:\/\//i.test(item.url) && typeof item.source === 'string');
}

export async function fetchHandler(request: Request, env: WorkerEnv): Promise<Response> {
  const url = new URL(request.url);
  if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: JSON_HEADERS });
  if (request.method === 'GET' && url.pathname === '/api/health') {
    return json({ ok: true, service: 'XAUUSD News Intelligence', apiVersion: 1, serverTime: new Date().toISOString(), configured: { registrationCode: Boolean(env.DEVICE_REGISTRATION_CODE), fcm: Boolean(env.FCM_PROJECT_ID && env.FCM_CLIENT_EMAIL && env.FCM_PRIVATE_KEY) } });
  }
  if (request.method === 'GET' && url.pathname === '/api/news') {
    try {
      const [feedRaw, statusRaw] = await Promise.all([env.DATA.get('news:latest'), env.DATA.get('status:latest')]);
      let feed: StoredFeed = { items: [], lastUpdatedAt: '' };
      let status: RunStatus | null = null;
      try { if (feedRaw) feed = JSON.parse(feedRaw) as StoredFeed; } catch { /* malformed cache is surfaced below */ }
      try { if (statusRaw) status = JSON.parse(statusRaw) as RunStatus; } catch { /* status is optional */ }
      return json({ items: Array.isArray(feed.items) ? feed.items : [], calendarEvents: Array.isArray(feed.calendarEvents) ? feed.calendarEvents : [], lastUpdatedAt: feed.lastUpdatedAt || null, status, message: feedRaw ? null : 'Backend belum menjalankan pemeriksaan terjadwal pertama.' });
    } catch {
      return json({ error: 'Backend storage sementara tidak tersedia.', items: [], lastUpdatedAt: null }, 503);
    }
  }
  if (url.pathname === '/api/register-device' && request.method === 'POST') {
    if (!env.DEVICE_REGISTRATION_CODE) return json({ error: 'Kode registrasi perangkat belum dikonfigurasi di backend.' }, 503);
    const parsedBody = await readLimitedJson(request, 8192);
    if (!parsedBody.ok) return json({ error: parsedBody.message }, parsedBody.status);
    const body = parsedBody.value as { token?: unknown; code?: unknown };
    if (typeof body.code !== 'string' || body.code.length > 128 || body.code !== env.DEVICE_REGISTRATION_CODE) return json({ error: 'Kode registrasi salah.' }, 403);
    if (typeof body.token !== 'string' || body.token.length < 20 || body.token.length > 4096 || /[\s\u0000-\u001f]/.test(body.token)) return json({ error: 'Token perangkat tidak valid.' }, 400);
    const tokenHash = await sha256(body.token);
    let devices: Array<{ id: string; token: string }> = [];
    try { const stored = await env.DATA.get('device:tokens'); const parsed: unknown = stored ? JSON.parse(stored) : []; if (Array.isArray(parsed)) devices = parsed.filter((d): d is { id: string; token: string } => Boolean(d && typeof d.id === 'string' && typeof d.token === 'string')); } catch { devices = []; }
    const existing = devices.some(device => device.id === tokenHash);
    if (!existing && devices.length >= 5) return json({ error: 'Batas lima perangkat tercapai. Hapus perangkat lama dari aplikasi sebelum menambahkan perangkat baru.' }, 429);
    devices = [...devices.filter(device => device.id !== tokenHash), { id: tokenHash, token: body.token }];
    await env.DATA.put('device:tokens', JSON.stringify(devices), { expirationTtl: 60 * 60 * 24 * 60 });
    return json({ ok: true, message: 'Perangkat terdaftar. Token tidak ditampilkan kembali.' });
  }
  if ((url.pathname === '/api/register-device' && request.method === 'DELETE') || (url.pathname === '/api/unregister-device' && request.method === 'POST')) {
    if (!env.DEVICE_REGISTRATION_CODE) return json({ error: 'Kode registrasi perangkat belum dikonfigurasi.' }, 503);
    const parsedBody = await readLimitedJson(request, 8192);
    if (!parsedBody.ok) return json({ error: parsedBody.message }, parsedBody.status);
    const body = parsedBody.value as { token?: unknown; code?: unknown };
    if (body.code !== env.DEVICE_REGISTRATION_CODE || typeof body.token !== 'string') return json({ error: 'Kode atau token tidak valid.' }, 403);
    let devices: Array<{ id: string; token: string }> = [];
    try { const stored = await env.DATA.get('device:tokens'); const parsed: unknown = stored ? JSON.parse(stored) : []; if (Array.isArray(parsed)) devices = parsed.filter((d): d is { id: string; token: string } => Boolean(d && typeof d.id === 'string' && typeof d.token === 'string')); } catch { devices = []; }
    const tokenHash = await sha256(body.token);
    await env.DATA.put('device:tokens', JSON.stringify(devices.filter(device => device.id !== tokenHash)), { expirationTtl: 60 * 60 * 24 * 60 });
    return json({ ok: true, message: 'Perangkat dihapus dari daftar push.' });
  }
  return json({ error: 'Endpoint tidak ditemukan.' }, 404);
}

export async function scheduledHandler(env: WorkerEnv, nowMs = Date.now()): Promise<RunStatus> {
  const warnings: string[] = [];
  const [candidateResult, spotResult, calendarResult] = await Promise.all([fetchNewsCandidates(env, nowMs), fetchSpotHistory(nowMs), fetchEconomicCalendar(nowMs)]);
  if (candidateResult.sourceStatus.every(s => !s.ok)) warnings.push('Semua sumber berita gagal diakses; feed sebelumnya dipertahankan.');
  if (!spotResult.available) warnings.push(`Zona harga tidak tersedia: ${spotResult.reason}.`);
  if (!calendarResult.ok) warnings.push(`Kalender ekonomi tidak tersedia: ${calendarResult.error}.`);
  const zone = spotResult.available ? calculateScenarioZone(spotResult.observations, nowMs, spotResult.currentPrice) : scenarioUnavailable(spotResult.reason);
  const rawFeed = await env.DATA.get('news:latest');
  let previous: AlertItem[] = [];
  let previousCalendar: CalendarEvent[] = [];
  try { const parsed = rawFeed ? JSON.parse(rawFeed) as StoredFeed : null; if (parsed && Array.isArray(parsed.items)) previous = parsed.items; if (parsed && Array.isArray(parsed.calendarEvents)) previousCalendar = parsed.calendarEvents; } catch { warnings.push('Cache feed sebelumnya rusak dan tidak dapat dipakai.'); }
  const calendarEvents = calendarResult.ok ? calendarResult.events : previousCalendar;
  const merged = new Map<string, AlertItem>();
  for (const item of previous) if (item?.key && item.title) merged.set(item.key, item);
  const recentForAlerts: AlertItem[] = [];
  for (const item of candidateResult.items) {
    if (!validNews(item)) continue;
    const publishedMs = Date.parse(item.publishedAt);
    if (!Number.isFinite(publishedMs) || publishedMs > nowMs + 5 * 60_000) continue;
    const classification = classifyNews(item);
    const key = makeDeduplicationKey(item);
    const newsItem: AlertItem = { ...item, ...classification, key, scenario: classification.level === 'NORMAL' ? undefined : zone };
    // Retain a seven-day feed, but only newly detected items from the last 24 hours can alert.
    if (nowMs - publishedMs <= 7 * 24 * 60 * 60_000) merged.set(key, newsItem);
    if (nowMs - publishedMs <= 24 * 60 * 60_000 && classification.level !== 'NORMAL') recentForAlerts.push(newsItem);
  }
  const items = [...merged.values()].map(item => item.level === 'NORMAL' ? item : { ...item, scenario: zone }).sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt)).slice(0, 100);
  const dispatch = await dispatchNewAlerts(env, recentForAlerts.sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt)).slice(0, 5));
  const status: RunStatus = { lastRunAt: new Date(nowMs).toISOString(), sourceStatus: candidateResult.sourceStatus, calendar: { ok: calendarResult.ok, count: calendarEvents.length, ...(!calendarResult.ok ? { error: calendarResult.error } : {}) }, spot: spotResult.available ? { available: true, currentPrice: spotResult.currentPrice, asOf: new Date(spotResult.asOfMs).toISOString() } : { available: false, reason: spotResult.reason }, dispatch, warnings };
  await Promise.all([
    env.DATA.put('news:latest', JSON.stringify({ items, calendarEvents, lastUpdatedAt: new Date(nowMs).toISOString() } satisfies StoredFeed), { expirationTtl: 60 * 60 * 24 * 8 }),
    env.DATA.put('status:latest', JSON.stringify(status), { expirationTtl: 60 * 60 * 24 * 8 }),
  ]);
  return status;
}

export default {
  fetch: fetchHandler,
  scheduled(_event: unknown, env: WorkerEnv, ctx: WorkerContext) { ctx.waitUntil(scheduledHandler(env)); },
};
