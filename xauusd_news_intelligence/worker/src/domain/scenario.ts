import type { ScenarioZoneResult, SpotObservation } from './types.ts';

const MAX_AGE_MS = 15 * 60_000;
const FOUR_HOURS_MS = 4 * 60 * 60_000;
const MAX_TARGET_GAP_MS = 3 * 60_000;
const MIN_RETURNS = 20;

function quantile(sorted: number[], q: number): number {
  if (sorted.length === 1) return sorted[0]!;
  const position = (sorted.length - 1) * q;
  const lowerIndex = Math.floor(position);
  const upperIndex = Math.ceil(position);
  const weight = position - lowerIndex;
  return sorted[lowerIndex]! * (1 - weight) + sorted[upperIndex]! * weight;
}

export function calculateScenarioZone(prices: SpotObservation[], nowMs: number, currentPriceOverride?: number): ScenarioZoneResult {
  if (!Array.isArray(prices) || prices.some(p => !Number.isFinite(p?.price) || p.price <= 0 || !Number.isFinite(p?.timestampMs) || p.timestampMs > nowMs + 60_000)) {
    return { available: false, reason: 'invalid_data', message: 'Data harga tidak valid; zona harga tidak dihitung.' };
  }
  if (currentPriceOverride !== undefined && (!Number.isFinite(currentPriceOverride) || currentPriceOverride <= 0)) return { available: false, reason: 'invalid_data', message: 'Harga spot saat ini tidak valid; zona harga tidak dihitung.' };
  const ordered = [...prices].sort((a, b) => a.timestampMs - b.timestampMs);
  const latest = ordered.at(-1);
  if (!latest || nowMs - latest.timestampMs > MAX_AGE_MS || nowMs < latest.timestampMs - 60_000) {
    return { available: false, reason: 'stale_data', message: 'Data harga kedaluwarsa; zona harga tidak dihitung.' };
  }
  if (ordered.length < MIN_RETURNS + 2) {
    return { available: false, reason: 'insufficient_history', message: 'Riwayat harga belum cukup untuk menghitung skenario empat jam.' };
  }
  const returns: number[] = [];
  // Locate the nearest actual observation four hours earlier instead of assuming
  // a candle cadence. XAUS currently documents a two-minute sampled series;
  // other providers may use five-minute sampling.
  for (let i = 1; i < ordered.length; i++) {
    const current = ordered[i]!;
    const target = current.timestampMs - FOUR_HOURS_MS;
    let low = 0; let high = i;
    while (low < high) {
      const mid = Math.floor((low + high) / 2);
      if (ordered[mid]!.timestampMs < target) low = mid + 1;
      else high = mid;
    }
    const candidates = [low - 1, low].filter(index => index >= 0 && index < i);
    let baseIndex = -1; let bestGap = Number.POSITIVE_INFINITY;
    for (const index of candidates) {
      const gap = Math.abs(ordered[index]!.timestampMs - target);
      if (gap < bestGap) { bestGap = gap; baseIndex = index; }
    }
    if (baseIndex < 0 || bestGap > MAX_TARGET_GAP_MS) continue;
    const base = ordered[baseIndex]!;
    const elapsed = current.timestampMs - base.timestampMs;
    if (elapsed < 3.5 * 60 * 60_000 || elapsed > 4.5 * 60 * 60_000) continue;
    returns.push(current.price / base.price - 1);
  }
  if (returns.length < MIN_RETURNS) {
    return { available: false, reason: 'insufficient_history', message: 'Sampel perubahan empat jam belum cukup; zona harga tidak dihitung.' };
  }
  const sorted = returns.sort((a, b) => a - b);
  const q10 = quantile(sorted, 0.1);
  const q90 = quantile(sorted, 0.9);
  const currentPrice = currentPriceOverride ?? latest.price;
  const lower = currentPrice * (1 + q10);
  const upper = currentPrice * (1 + q90);
  if (!Number.isFinite(lower) || !Number.isFinite(upper) || lower <= 0 || lower >= upper) {
    return { available: false, reason: 'invalid_data', message: 'Sampel harga menghasilkan rentang yang tidak valid.' };
  }
  return { available: true, currentPrice, lower, upper, horizonHours: 4, sampleCount: sorted.length, coverageHours: Math.round((latest.timestampMs - ordered[0]!.timestampMs) / 3_600_000 * 10) / 10, lowerReturnPct: q10 * 100, upperReturnPct: q90 * 100, method: 'rolling_4h_empirical_q10_q90', uncertainty: 'Rentang historis indikatif, bukan target, prediksi terkalibrasi, atau jaminan. Sampel dapat saling tumpang tindih.' };
}
