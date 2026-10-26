import type { NormalizedNews } from './types.ts';

export function makeDeduplicationKey(item: NormalizedNews): string {
  const id = typeof item.id === 'string' ? item.id.trim() : '';
  if (id && !/^https?:\/\//i.test(id)) return `id:${id.toLowerCase()}`;
  try {
    const url = new URL(/^https?:\/\//i.test(id) ? id : item.url);
    url.hash = '';
    for (const key of [...url.searchParams.keys()]) {
      if (/^(utm_.+|fbclid|gclid|mc_cid|mc_eid|ref|source)$/i.test(key)) url.searchParams.delete(key);
    }
    url.hostname = url.hostname.toLowerCase();
    url.pathname = url.pathname.replace(/\/+$/, '') || '/';
    url.searchParams.sort();
    return `url:${url.toString()}`;
  } catch {
    const fingerprint = `${item.source || ''}|${item.title || ''}`.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
    return `text:${fingerprint}`;
  }
}
