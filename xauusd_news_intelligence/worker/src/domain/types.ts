export type ImpactLevel = 'NORMAL' | 'MEDIUM' | 'HIGH';
export interface NormalizedNews {
  id?: string;
  title: string;
  summary?: string;
  source: string;
  url: string;
  publishedAt: string;
  detectedAt: string;
}
export interface Classification {
  level: ImpactLevel;
  reason: string;
  bullish: string;
  bearish: string;
  tags: string[];
}
export interface SpotObservation { timestampMs: number; price: number; }
export type ScenarioZoneResult =
  | { available: true; currentPrice: number; lower: number; upper: number; horizonHours: 4; sampleCount: number; coverageHours: number; lowerReturnPct: number; upperReturnPct: number; method: 'rolling_4h_empirical_q10_q90'; uncertainty: string }
  | { available: false; reason: 'invalid_data' | 'stale_data' | 'insufficient_history'; message: string };
export interface NewsItem extends NormalizedNews, Classification { key: string; }
