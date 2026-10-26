import type { Classification, NormalizedNews } from './types.ts';

const HIGH_PATTERNS: Array<[RegExp, string, string[]]> = [
  [/emergency|surprise|unexpected|unscheduled|emergency meeting|emergency rate/i, 'Ada indikasi keputusan atau kejadian makro yang tidak rutin dan dapat mengubah ekspektasi suku bunga atau risiko pasar.', ['surprise', 'central-bank']],
  [/federal reserve|\bfed\b|fomc|interest rate|rate decision|rate cut|rate hike|policy rate|powell|inflation|\bcpi\b|\bpce\b|nonfarm payroll|\bnfp\b|unemployment report/i, 'Berita menyangkut suku bunga, inflasi, atau tenaga kerja AS yang dapat mengubah ekspektasi imbal hasil obligasi dan dolar.', ['macro-us']],
  [/war declared|invasion|missile attack|military strike|nuclear|ceasefire collapsed|strait of hormuz|oil supply disruption/i, 'Peristiwa konflik atau gangguan pasokan berpotensi mengubah permintaan aset aman dan ekspektasi inflasi.', ['geopolitics', 'risk-sentiment']],
];
const MEDIUM_PATTERNS: Array<[RegExp, string, string[]]> = [
  [/tariff|sanction|trade war|trade restriction|trade deal|customs duty/i, 'Perubahan tarif atau sanksi dapat memengaruhi inflasi, pertumbuhan, dolar, dan sentimen risiko.', ['trade-policy']],
  [/treasury yield|bond yield|dollar index|\bus dollar\b|\busd\b|jobs data|employment|pmi|retail sales|gdp|central bank|rate outlook|gold reserve|central bank gold/i, 'Data atau kebijakan ini dapat mengubah ekspektasi suku bunga, imbal hasil, dolar, atau permintaan emas.', ['macro-market']],
  [/ceasefire|peace talks|border clash|attack|conflict|sanctions|election|political crisis|oil price|crude supply/i, 'Perkembangan geopolitik atau energi dapat mengubah sentimen risiko dan permintaan aset aman.', ['geopolitics']],
  [/gold price|bullion|gold demand|gold buying|precious metals|xauusd/i, 'Berita terkait emas dapat memengaruhi sentimen atau ekspektasi permintaan, tetapi dampaknya perlu dikonfirmasi oleh pasar.', ['gold-market']],
];
const NON_NEWS = /sports?|football|soccer|celebrity|movie|recipe|fashion|local club|new goalkeeper|horoscope/i;

export function classifyNews(item: NormalizedNews): Classification {
  const title = typeof item?.title === 'string' ? item.title.trim() : '';
  const summary = typeof item?.summary === 'string' ? item.summary.trim() : '';
  const text = `${title} ${summary}`.slice(0, 5000);
  if (!text.trim() || NON_NEWS.test(title)) {
    return { level: 'NORMAL', reason: 'Belum ditemukan hubungan langsung yang cukup jelas dengan penggerak utama harga emas.', bullish: 'belum ada alasan langsung yang cukup kuat untuk menyimpulkan harga emas naik dari berita ini saja.', bearish: 'belum ada alasan langsung yang cukup kuat untuk menyimpulkan harga emas turun dari berita ini saja.', tags: [] };
  }
  for (const [pattern, reason, tags] of HIGH_PATTERNS) {
    if (pattern.test(text)) return { level: 'HIGH', reason, bullish: 'Jika berita ini meningkatkan ketidakpastian atau menekan ekspektasi imbal hasil riil AS, emas dapat menguat; konfirmasi melalui harga, dolar, dan yield.', bearish: 'Jika berita ini mendorong ekspektasi suku bunga/yield AS lebih tinggi atau memperkuat dolar, emas dapat melemah; reaksi pasar bisa berlawanan dari narasi awal.', tags };
  }
  for (const [pattern, reason, tags] of MEDIUM_PATTERNS) {
    if (pattern.test(text)) return { level: 'MEDIUM', reason, bullish: 'Jika dampaknya menekan dolar atau yield riil, atau meningkatkan permintaan aset aman, emas berpotensi naik.', bearish: 'Jika dampaknya memperkuat dolar atau menaikkan yield riil, emas berpotensi turun.', tags };
  }
  return { level: 'NORMAL', reason: 'Belum ditemukan pemicu yang cukup spesifik terkait suku bunga AS, dolar, yield, geopolitik, atau permintaan emas.', bullish: 'belum ada alasan langsung yang cukup kuat untuk menyimpulkan harga emas naik dari berita ini saja.', bearish: 'belum ada alasan langsung yang cukup kuat untuk menyimpulkan harga emas turun dari berita ini saja.', tags: [] };
}
