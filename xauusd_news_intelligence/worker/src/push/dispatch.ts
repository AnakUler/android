import type { NewsItem, ScenarioZoneResult } from '../domain/types.ts';
import { sendFcmNotification, type DeliveryResult, type PushPayload } from './fcm.ts';

interface KVListResult { keys: Array<{ name: string }>; list_complete?: boolean; cursor?: string; }
interface KVNamespaceLike {
  get(key: string): Promise<string | null>;
  put(key: string, value: string, options?: { expirationTtl?: number }): Promise<void>;
  list(options?: { prefix?: string; limit?: number; cursor?: string }): Promise<KVListResult>;
}
interface DispatchEnv { DATA: KVNamespaceLike; FCM_PROJECT_ID?: string; FCM_CLIENT_EMAIL?: string; FCM_PRIVATE_KEY?: string; send?: (token: string, payload: PushPayload) => Promise<DeliveryResult>; }
export interface AlertItem extends NewsItem { scenario?: ScenarioZoneResult; }
export interface DispatchSummary { sent: number; failed: number; skippedNormal: number; skippedDuplicate: number; skippedNoDevice: number; }

function formatScenario(scenario: ScenarioZoneResult | undefined): string {
  if (!scenario?.available) return 'Zona harga 4 jam: belum dapat dihitung karena data tidak cukup atau kedaluwarsa.';
  return `Zona historis 4 jam: bawah $${scenario.lower.toFixed(2)} | atas $${scenario.upper.toFixed(2)} per troy ounce (skenario, bukan target).`;
}
function payloadFor(item: AlertItem): PushPayload {
  const title = `[${item.level}] ${item.title}`;
  const body = `${item.reason} ${item.bullish} ${item.bearish} ${formatScenario(item.scenario)} Sumber: ${item.source}.`;
  return { title, body, data: { key: item.key, level: item.level, url: item.url, title: item.title } };
}
export async function dispatchNewAlerts(env: DispatchEnv, items: AlertItem[]): Promise<DispatchSummary> {
  const summary: DispatchSummary = { sent: 0, failed: 0, skippedNormal: 0, skippedDuplicate: 0, skippedNoDevice: 0 };
  const rawSent = await env.DATA.get('alerts:sent');
  let sentKeys: string[] = [];
  try { const parsed: unknown = rawSent ? JSON.parse(rawSent) : []; if (Array.isArray(parsed)) sentKeys = parsed.filter((v): v is string => typeof v === 'string').slice(-1500); } catch { sentKeys = []; }
  const sentSet = new Set(sentKeys);
  let storedDevices: Array<{ id: string; token: string }> = [];
  try { const rawDevices = await env.DATA.get('device:tokens'); const parsed: unknown = rawDevices ? JSON.parse(rawDevices) : []; if (Array.isArray(parsed)) storedDevices = parsed.filter((d): d is { id: string; token: string } => Boolean(d && typeof d.id === 'string' && typeof d.token === 'string')); } catch { storedDevices = []; }
  const deviceTokens = storedDevices.slice(0, 5).map(device => device.token).filter(token => token.length >= 20 && token.length <= 4096);
  for (const item of items) {
    if (item.level === 'NORMAL') { summary.skippedNormal++; continue; }
    if (sentSet.has(item.key)) { summary.skippedDuplicate++; continue; }
    if (deviceTokens.length === 0) { summary.skippedNoDevice++; continue; }
    let allSent = true;
    const payload = payloadFor(item);
    for (const token of deviceTokens) {
      const result = env.send ? await env.send(token, payload) : await sendFcmNotification(env, token, payload);
      if (result.ok) summary.sent++;
      else { allSent = false; summary.failed++; }
    }
    if (allSent) { sentSet.add(item.key); sentKeys.push(item.key); }
  }
  if (sentKeys.length !== sentKeys.slice(-1500).length || summary.sent > 0) {
    await env.DATA.put('alerts:sent', JSON.stringify(sentKeys.slice(-1500)), { expirationTtl: 60 * 60 * 24 * 14 });
  }
  return summary;
}
