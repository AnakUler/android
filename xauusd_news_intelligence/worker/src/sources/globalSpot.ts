import type { SpotObservation } from '../domain/types.ts';

export type SpotHistoryResult =
  | { available: true; observations: SpotObservation[]; currentPrice: number; asOfMs: number; source: 'XAUS global indicative spot' }
  | { available: false; reason: string; source: 'XAUS global indicative spot' };
type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
const SOURCE = 'XAUS global indicative spot' as const;

export async function fetchSpotHistory(nowMs: number, fetcher: Fetcher = fetch): Promise<SpotHistoryResult> {
  try {
    const fresh = Math.floor(nowMs / 1000);
    const [spotResponse, historyResponse] = await Promise.all([
      fetcher(`https://xaus.com/api/v1/spot?compact=1&fresh=${fresh}`, { headers: { accept: 'application/json' }, signal: AbortSignal.timeout(8000) }),
      fetcher(`https://xaus.com/api/v1/intraday?symbol=xau&hours=48&fresh=${fresh}`, { headers: { accept: 'application/json' }, signal: AbortSignal.timeout(8000) }),
    ]);
    if (!spotResponse.ok) return { available: false, reason: `spot_http_${spotResponse.status}`, source: SOURCE };
    if (!historyResponse.ok) return { available: false, reason: `history_http_${historyResponse.status}`, source: SOURCE };
    const [spot, history] = await Promise.all([
      spotResponse.json() as Promise<{ spot_usd_oz?: unknown; updated_at?: unknown; price_as_of?: unknown; data_state?: { status?: unknown; as_of?: unknown; age_seconds?: unknown } }>,
      historyResponse.json() as Promise<{ points?: Array<{ t?: unknown; p?: unknown }>; updated_at?: unknown; data_state?: { status?: unknown; as_of?: unknown; age_seconds?: unknown }; coverage_seconds?: unknown }>,
    ]);
    const currentPrice = Number(spot.spot_usd_oz);
    const responseAt = Date.parse(typeof spot.updated_at === 'string' ? spot.updated_at : '');
    const priceAsOf = Date.parse(typeof spot.price_as_of === 'string' ? spot.price_as_of : typeof spot.data_state?.as_of === 'string' ? spot.data_state.as_of : typeof spot.updated_at === 'string' ? spot.updated_at : '');
    const reportedAgeSeconds = Number(spot.data_state?.age_seconds);
    if (spot.data_state?.status !== 'fresh' || !Number.isFinite(currentPrice) || currentPrice <= 0) return { available: false, reason: 'spot_stale_or_malformed', source: SOURCE };
    if (!Number.isFinite(responseAt) || responseAt > nowMs + 60_000 || nowMs - responseAt > 5 * 60_000 || !Number.isFinite(priceAsOf) || nowMs - priceAsOf > 5 * 60_000 || (Number.isFinite(reportedAgeSeconds) && reportedAgeSeconds > 300)) {
      return { available: false, reason: 'spot_response_stale', source: SOURCE };
    }
    if (history.data_state?.status !== 'fresh' || !Array.isArray(history.points)) return { available: false, reason: 'intraday_stale_or_malformed', source: SOURCE };
    const observations = history.points.flatMap((point): SpotObservation[] => {
      const t = Number(point.t); const p = Number(point.p);
      if (!Number.isFinite(t) || !Number.isFinite(p) || p <= 0) return [];
      return [{ timestampMs: t * 1000, price: p }];
    }).sort((a, b) => a.timestampMs - b.timestampMs);
    const latest = observations.at(-1);
    if (!latest || nowMs - latest.timestampMs > 15 * 60_000 || observations.length < 68) return { available: false, reason: 'intraday_history_insufficient_or_stale', source: SOURCE };
    return { available: true, observations, currentPrice, asOfMs: priceAsOf, source: SOURCE };
  } catch (error) {
    return { available: false, reason: error instanceof Error ? error.message.slice(0, 120) : 'provider_error', source: SOURCE };
  }
}
