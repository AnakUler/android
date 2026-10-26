export interface CalendarEvent {
  id: string;
  title: string;
  source: 'U.S. Bureau of Labor Statistics calendar';
  url: string;
  scheduledAt: string;
  impact: 'MEDIUM' | 'HIGH';
  timezone: string;
}
export type CalendarResult = { ok: true; events: CalendarEvent[] } | { ok: false; events: CalendarEvent[]; error: string };
type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
const SOURCE = 'U.S. Bureau of Labor Statistics calendar' as const;
const RELEVANT = /consumer price index|producer price|employment situation|real earnings|employment cost index|productivity and costs|import and export price|job openings and labor turnover|unemployment|employment cost|compensation|wage|earnings/i;
const HIGH_IMPACT = /consumer price index|producer price|employment situation|employment cost index/i;

function unescapeIcs(value: string): string {
  return value.replace(/\\n/gi, ' ').replace(/\\,/g, ',').replace(/\\;/g, ';').replace(/\\\\/g, '\\').trim();
}
function localDateTimeToUtc(year: number, month: number, day: number, hour: number, minute: number, second: number, timezone: string): number {
  const targetAsUtc = Date.UTC(year, month - 1, day, hour, minute, second);
  let guess = targetAsUtc;
  const formatter = new Intl.DateTimeFormat('en-US', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
  for (let i = 0; i < 4; i++) {
    const parts = formatter.formatToParts(new Date(guess));
    const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
    const observedAsUtc = Date.UTC(Number(values.year), Number(values.month) - 1, Number(values.day), Number(values.hour), Number(values.minute), Number(values.second));
    const correction = targetAsUtc - observedAsUtc;
    guess += correction;
    if (correction === 0) break;
  }
  return guess;
}
function parseStart(line: string): number | null {
  const colon = line.indexOf(':');
  if (colon < 0) return null;
  const metadata = line.slice(0, colon);
  const value = line.slice(colon + 1).trim();
  if (/^\d{8}$/.test(value)) return null; // Date-only items have no confirmed release time.
  const match = value.match(/^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})?(Z)?$/);
  if (!match) return null;
  const [, year, month, day, hour, minute, second = '00', zulu] = match;
  if (zulu) return Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), Number(second));
  const timezone = (metadata.match(/TZID=([^;:]+)/i)?.[1] || 'America/New_York').replace(/^"|"$/g, '');
  try { return localDateTimeToUtc(Number(year), Number(month), Number(day), Number(hour), Number(minute), Number(second), timezone); }
  catch { return null; }
}

export function parseEconomicCalendar(ics: string, nowMs: number): CalendarEvent[] {
  const unfolded = ics.replace(/\r?\n[ \t]/g, '').split(/\r?\n/);
  const events: CalendarEvent[] = [];
  let current: string[] | null = null;
  for (const line of unfolded) {
    if (line.trim() === 'BEGIN:VEVENT') { current = []; continue; }
    if (line.trim() === 'END:VEVENT') {
      if (current) {
        const startLine = current.find(value => /^DTSTART(?:;[^:]*)?:/i.test(value));
        const titleLine = current.find(value => /^SUMMARY(?:;[^:]*)?:/i.test(value));
        const uidLine = current.find(value => /^UID(?:;[^:]*)?:/i.test(value));
        const urlLine = current.find(value => /^URL(?:;[^:]*)?:/i.test(value));
        const timestamp = startLine ? parseStart(startLine) : null;
        const title = titleLine ? unescapeIcs(titleLine.slice(titleLine.indexOf(':') + 1)) : '';
        if (timestamp !== null && timestamp >= nowMs && timestamp <= nowMs + 14 * 24 * 60 * 60_000 && title && RELEVANT.test(title)) {
          events.push({
            id: uidLine ? unescapeIcs(uidLine.slice(uidLine.indexOf(':') + 1)) : `${timestamp}:${title.toLowerCase()}`,
            title,
            source: SOURCE,
            url: urlLine ? unescapeIcs(urlLine.slice(urlLine.indexOf(':') + 1)) : 'https://www.bls.gov/schedule/news_release/bls.ics',
            scheduledAt: new Date(timestamp).toISOString(),
            impact: HIGH_IMPACT.test(title) ? 'HIGH' : 'MEDIUM',
            timezone: 'America/New_York',
          });
        }
      }
      current = null;
      continue;
    }
    if (current) current.push(line);
  }
  const seen = new Set<string>();
  return events.sort((a, b) => Date.parse(a.scheduledAt) - Date.parse(b.scheduledAt)).filter(event => {
    if (seen.has(event.id)) return false;
    seen.add(event.id);
    return true;
  }).slice(0, 60);
}

export async function fetchEconomicCalendar(nowMs: number, fetcher: Fetcher = fetch): Promise<CalendarResult> {
  try {
    const response = await fetcher('https://www.bls.gov/schedule/news_release/bls.ics', { headers: { accept: 'text/calendar, text/plain' }, signal: AbortSignal.timeout(8000) });
    if (!response.ok) return { ok: false, events: [], error: `BLS calendar HTTP ${response.status}` };
    const raw = await response.text();
    return { ok: true, events: parseEconomicCalendar(raw, nowMs) };
  } catch (error) {
    return { ok: false, events: [], error: error instanceof Error ? error.message.slice(0, 120) : 'Calendar provider error' };
  }
}
