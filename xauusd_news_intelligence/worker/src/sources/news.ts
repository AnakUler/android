import type { NormalizedNews } from '../domain/types.ts';

export interface SourceStatus { source: string; ok: boolean; count: number; error?: string; }
export interface NewsCandidatesResult { items: NormalizedNews[]; sourceStatus: SourceStatus[]; }
type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

const GDELT_URL = 'https://api.gdeltproject.org/api/v2/doc/doc?query=%28gold%20OR%20XAUUSD%20OR%20%22Federal%20Reserve%22%20OR%20inflation%20OR%20tariff%20OR%20war%20OR%20sanctions%20OR%20%22Treasury%20yield%22%29&mode=ArtList&format=json&maxrecords=50&sort=DateDesc';
const OFFICIAL_FEEDS = [
  { name: 'Federal Reserve', url: 'https://www.federalreserve.gov/feeds/press_all.xml' },
  { name: 'US Bureau of Labor Statistics', url: 'https://www.bls.gov/feed/bls_latest.rss' },
];

function decodeXml(value: string): string {
  return value.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1').replace(/<[^>]+>/g, ' ')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'")
    .replace(/\s+/g, ' ').trim();
}
function xmlField(block: string, names: string[]): string {
  for (const name of names) {
    const re = new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${name}>`, 'i');
    const match = block.match(re);
    if (match?.[1]) return decodeXml(match[1]);
  }
  return '';
}
function safeDate(value: string, nowMs: number): string {
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && parsed <= nowMs + 5 * 60_000 ? new Date(parsed).toISOString() : '';
}
async function getText(url: string, fetcher: Fetcher): Promise<string> {
  const response = await fetcher(url, { headers: { 'user-agent': 'XAUUSD-News-Intelligence/1.0 (personal research app)', accept: 'application/json, application/rss+xml, application/xml, text/xml' }, signal: AbortSignal.timeout(8000) });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.text();
}
function parseRss(xml: string, source: string, nowMs: number): NormalizedNews[] {
  return [...xml.matchAll(/<(?:item|entry)\b[^>]*>([\s\S]*?)<\/(?:item|entry)>/gi)].slice(0, 40).flatMap((match, i) => {
    const block = match[1] ?? '';
    const title = xmlField(block, ['title']);
    let url = xmlField(block, ['link', 'guid', 'id']);
    const linkAttr = block.match(/<link[^>]+href=["']([^"']+)["']/i)?.[1];
    if (!url && linkAttr) url = linkAttr;
    if (!/^https?:\/\//i.test(url)) url = linkAttr ?? '';
    if (!title || !url) return [];
    return [{ id: xmlField(block, ['guid', 'id']) || undefined, title, summary: xmlField(block, ['description', 'summary', 'content']), source, url, publishedAt: safeDate(xmlField(block, ['pubDate', 'published', 'updated', 'dc:date']), nowMs), detectedAt: new Date(nowMs).toISOString() }];
  });
}
export async function fetchNewsCandidates(_env: unknown, nowMs: number, fetcher: Fetcher = fetch): Promise<NewsCandidatesResult> {
  const results = await Promise.allSettled([
    getText(GDELT_URL, fetcher).then(raw => {
      const json = JSON.parse(raw) as { articles?: Array<Record<string, unknown>> };
      const articles = Array.isArray(json.articles) ? json.articles : [];
      const items = articles.flatMap((a): NormalizedNews[] => {
        const title = typeof a.title === 'string' ? a.title.trim() : '';
        const url = typeof a.url === 'string' ? a.url : '';
        if (!title || !/^https?:\/\//i.test(url)) return [];
        const seen = typeof a.seendate === 'string' ? a.seendate.replace(/^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/, '$1-$2-$3T$4:$5:$6Z') : '';
        return [{ title, summary: typeof a.snippet === 'string' ? a.snippet : '', source: typeof a.domain === 'string' ? a.domain : 'GDELT', url, publishedAt: safeDate(seen, nowMs), detectedAt: new Date(nowMs).toISOString() }];
      });
      return { source: 'GDELT', items };
    }),
    ...OFFICIAL_FEEDS.map(feed => getText(feed.url, fetcher).then(xml => ({ source: feed.name, items: parseRss(xml, feed.name, nowMs) }))),
  ]);
  const items: NormalizedNews[] = [];
  const sourceStatus: SourceStatus[] = [];
  for (let i = 0; i < results.length; i++) {
    const result = results[i]!;
    const name = i === 0 ? 'GDELT' : OFFICIAL_FEEDS[i - 1]!.name;
    if (result.status === 'fulfilled') {
      items.push(...result.value.items);
      sourceStatus.push({ source: result.value.source, ok: true, count: result.value.items.length });
    } else {
      sourceStatus.push({ source: name, ok: false, count: 0, error: result.reason instanceof Error ? result.reason.message.slice(0, 120) : 'Unknown source error' });
    }
  }
  const seen = new Set<string>();
  return { items: items.filter(item => { const key = `${item.url}|${item.title.toLowerCase()}`; if (seen.has(key)) return false; seen.add(key); return true; }), sourceStatus };
}
