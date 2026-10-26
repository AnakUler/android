import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { parseEconomicCalendar } from '../src/sources/economicCalendar.ts';

describe('official economic calendar', () => {
  it('parses future BLS releases with timezone-aware timestamps and impact levels', () => {
    const now = Date.parse('2026-11-02T12:00:00Z');
    const ics = [
      'BEGIN:VCALENDAR',
      'BEGIN:VEVENT',
      'UID:cpi-2026@example.gov',
      'DTSTART;TZID=America/New_York:20261110T083000',
      'SUMMARY:Consumer Price Index for October 2026',
      'URL:https://www.bls.gov/news.release/cpi.htm',
      'END:VEVENT',
      'BEGIN:VEVENT',
      'UID:earnings-2026@example.gov',
      'DTSTART;TZID=America/New_York:20261110T083000',
      'SUMMARY:Real Earnings for October 2026',
      'URL:https://www.bls.gov/news.release/realer.htm',
      'END:VEVENT',
      'END:VCALENDAR',
    ].join('\r\n');
    const events = parseEconomicCalendar(ics, now);
    assert.equal(events.length, 2);
    assert.equal(events[0]?.title, 'Consumer Price Index for October 2026');
    assert.equal(events[0]?.impact, 'HIGH');
    assert.equal(events[1]?.impact, 'MEDIUM');
    assert.equal(events[0]?.scheduledAt, '2026-11-10T13:30:00.000Z');
  });
  it('filters past events and irrelevant releases', () => {
    const now = Date.parse('2026-11-02T12:00:00Z');
    const ics = ['BEGIN:VEVENT','UID:old','DTSTART:20261001T123000Z','SUMMARY:Consumer Price Index','END:VEVENT','BEGIN:VEVENT','UID:irrelevant','DTSTART:20261101T120000Z','SUMMARY:Publications catalog update','END:VEVENT'].join('\n');
    assert.equal(parseEconomicCalendar(ics, now).length, 0);
  });
});
