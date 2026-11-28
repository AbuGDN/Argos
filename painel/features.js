/* Argos · Painel — extras: dia e noite, arcos entre regiões, estreitos, ficha do país, comparação,
 * arquivo, atalhos de teclado, mercados com gráfico e imagem do globo para compartilhar.
 * Usa o estado e as funções de app.js (carregado antes). */
'use strict';

const RAD = Math.PI / 180;
const COMPARE_COLORS = ['#C9A227', '#E8E2D0', '#C8662B', '#6E8B6A'];
const STRAIT_POS = { hormuz: [56.3, 26.55], bab: [43.35, 12.6], suez: [32.35, 30.55], bosporus: [29.06, 41.12] };
const BROAD = ['eua', 'otan', 'asia', 'africa', 'ice', 'brasil', 'mediterraneo'];

// ---------------------------------------------------------------------------
// 1. Dia e noite: sombra onde é noite agora e relógios das capitais
// ---------------------------------------------------------------------------
function sunPosition(date = new Date()) {
  const d = (date.getTime() - Date.UTC(2000, 0, 1, 12)) / 86400000;
  const g = (357.529 + 0.98560028 * d) * RAD;
  const q = 280.459 + 0.98564736 * d;
  const L = (q + 1.915 * Math.sin(g) + 0.02 * Math.sin(2 * g)) * RAD;
  const e = (23.439 - 0.00000036 * d) * RAD;
  const decl = Math.asin(Math.sin(e) * Math.sin(L));
  const ra = Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L)) / RAD;
  const gmst = ((18.697374558 + 24.06570982441908 * d) % 24 + 24) % 24;
  let lon = ra - gmst * 15;
  lon = ((lon + 540) % 360) - 180;
  return { lat: decl / RAD, lon };
}

/** Faixa escura do lado da noite: a linha do terminador e o polo que está no escuro. */
function nightPolygon(date = new Date()) {
  const sun = sunPosition(date);
  const decl = (Math.abs(sun.lat) < 0.1 ? 0.1 : sun.lat) * RAD;
  const line = [];
  for (let x = -180; x <= 180; x += 2) {
    line.push([x, Math.atan(-Math.cos((x - sun.lon) * RAD) / Math.tan(decl)) / RAD]);
  }
  const pole = decl > 0 ? -89 : 89;
  return { type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: [[...line, [180, pole], [-180, pole], line[0]]] } };
}

function isDay(lat, lon, date = new Date()) {
  const s = sunPosition(date);
  const h = (lon - s.lon) * RAD;
  return Math.sin(lat * RAD) * Math.sin(s.lat * RAD) + Math.cos(lat * RAD) * Math.cos(s.lat * RAD) * Math.cos(h) > 0;
}

function visibleOnGlobe(lon, lat, limit = 80) {
  const c = map.getCenter();
  const d = Math.acos(Math.min(1, Math.sin(lat * RAD) * Math.sin(c.lat * RAD) + Math.cos(lat * RAD) * Math.cos(c.lat * RAD) * Math.cos((lon - c.lng) * RAD)));
  return d < limit * RAD;
}

/** Marcadores HTML atrás do globo ficariam visíveis através dele: esconde os do outro lado. */
function hideBackside() {
  const all = [...markers.regions.values(), ...markers.carriers, ...capMarkers];
  for (const m of all) {
    const { lng, lat } = m.getLngLat();
    m.getElement().style.visibility = visibleOnGlobe(lng, lat, 84) ? '' : 'hidden';
  }
}
map.on('move', hideBackside);

const capMarkers = [];
function renderNight() {
  if (!map.getSource('night')) return;
  map.getSource('night').setData(S.layers.night ? nightPolygon() : fc([]));
  capMarkers.forEach((m) => m.remove());
  capMarkers.length = 0;
  if (!S.layers.night) return;
  for (const c of S.geo?.capitals || []) {
    const el = document.createElement('div');
    el.className = 'cap';
    const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: c.zone }).format(new Date());
    el.innerHTML = `${isDay(c.lat, c.lon) ? '☀' : '☾'} ${esc(c.city)} <b>${time}</b>`;
    capMarkers.push(new maplibregl.Marker({ element: el, anchor: 'left', offset: [6, 0] }).setLngLat([c.lon, c.lat]).addTo(map));
  }
  hideBackside();
}
setInterval(renderNight, 60000);

// ---------------------------------------------------------------------------
// 2. Arcos entre regiões citadas juntas nas notícias das últimas 36 h
// ---------------------------------------------------------------------------
function regionPairs() {
  const pts = S.geo?.points || {};
  const since = Date.now() - 36 * 3600000;
  const pairs = new Map();
  for (const c of S.feed?.clusters || []) {
    if (Date.parse(c.updated) < since) continue;
    const tags = [...new Set(c.tags)].filter((t) => pts[t]).sort();
    for (let i = 0; i < tags.length; i++) {
      for (let j = i + 1; j < tags.length; j++) {
        const key = `${tags[i]}|${tags[j]}`;
        if (!pairs.has(key)) pairs.set(key, { a: tags[i], b: tags[j], ids: [] });
        pairs.get(key).ids.push(c.id);
      }
    }
  }
  return [...pairs.values()].filter((p) => p.ids.length >= 2).sort((x, y) => y.ids.length - x.ids.length).slice(0, 14);
}

/** Pontos do grande círculo entre dois lugares (a menor rota sobre o globo). */
function greatCircle([lon1, lat1], [lon2, lat2], steps = 64) {
  const toV = (lo, la) => [Math.cos(la * RAD) * Math.cos(lo * RAD), Math.cos(la * RAD) * Math.sin(lo * RAD), Math.sin(la * RAD)];
  const a = toV(lon1, lat1), b = toV(lon2, lat2);
  const dot = Math.min(1, Math.max(-1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
  const w = Math.acos(dot);
  if (w < 1e-6) return [[lon1, lat1], [lon2, lat2]];
  const out = [];
  let prevLon = null;
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const k1 = Math.sin((1 - t) * w) / Math.sin(w), k2 = Math.sin(t * w) / Math.sin(w);
    const v = [k1 * a[0] + k2 * b[0], k1 * a[1] + k2 * b[1], k1 * a[2] + k2 * b[2]];
    let lon = Math.atan2(v[1], v[0]) / RAD;
    const lat = Math.asin(v[2]) / RAD;
    // Longitude contínua (sem pular de 180 para -180 no meio do arco).
    if (prevLon != null) while (lon - prevLon > 180) lon -= 360;
    if (prevLon != null) while (prevLon - lon > 180) lon += 360;
    prevLon = lon;
    out.push([lon, lat]);
  }
  return out;
}

function renderArcs() {
  if (!map.getSource('arcs')) return;
  const pts = S.geo?.points || {};
  const feats = S.layers.arcs ? regionPairs().map((p) => ({
    type: 'Feature',
    geometry: { type: 'LineString', coordinates: greatCircle(pts[p.a], pts[p.b]) },
    properties: { a: p.a, b: p.b, n: p.ids.length, name: `${label(p.a)} ↔ ${label(p.b)}` },
  })) : [];
  S.arcFeatures = feats;
  map.getSource('arcs').setData(fc(feats));
}

// Traço andando ao longo dos arcos.
const DASHES = [[0, 4, 3], [0.5, 4, 2.5], [1, 4, 2], [1.5, 4, 1.5], [2, 4, 1], [2.5, 4, 0.5], [3, 4, 0], [0, 0.5, 3, 3.5], [0, 1, 3, 3], [0, 1.5, 3, 2.5], [0, 2, 3, 2], [0, 2.5, 3, 1.5], [0, 3, 3, 1], [0, 3.5, 3, 0.5]];
let dashStep = 0;
setInterval(() => {
  if (!S.layers.arcs || !map.getLayer('arcs-flow') || document.hidden || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  dashStep = (dashStep + 1) % DASHES.length;
  map.setPaintProperty('arcs-flow', 'line-dasharray', DASHES[dashStep]);
}, 90);

function openPair(a, b) {
  const pair = regionPairs().find((p) => p.a === a && p.b === b);
  const list = (pair?.ids || []).map(findCluster).filter(Boolean);
  S.story = null;
  showDetail(`<div class="detail-head"><div class="kicker">Ligação nas notícias</div><h2>${esc(label(a))} ↔ ${esc(label(b))}</h2>
    <div class="m">${list.length} notícias das últimas 36 h citam as duas regiões</div>
    <div style="margin-top:8px"><span class="chip" data-region="${a}">${esc(label(a))}</span><span class="chip" data-region="${b}">${esc(label(b))}</span></div></div>
    ${list.map(storyRow).join('')}`);
}

// ---------------------------------------------------------------------------
// 3. Estreitos: passagem de navios colorida pelo tráfego
// ---------------------------------------------------------------------------
function straitStatus(s) {
  if (s.avg7 == null) return 'sem dados';
  if (s.avg90 != null && s.avg90 > 5 && s.avg7 < s.avg90 * 0.6) return 'queda';
  return 'normal';
}
function renderStraits() {
  if (!map.getSource('straits')) return;
  const feats = [];
  if (S.layers.straits) {
    for (const s of S.radar?.straits?.items || []) {
      const pos = STRAIT_POS[s.id];
      if (pos) feats.push(pt(pos[0], pos[1], { id: s.id, name: s.name, status: straitStatus(s), avg7: s.avg7 ?? -1, avg90: s.avg90 ?? -1 }));
    }
  }
  map.getSource('straits').setData(fc(feats));
}

function sparkSvg(values, color, { w = 360, h = 60, baseline = null } = {}) {
  const v = (values || []).filter((x) => x != null);
  if (v.length < 2) return '';
  const min = Math.min(...v, baseline ?? Infinity), max = Math.max(...v, baseline ?? -Infinity);
  const range = max - min || 1;
  const step = w / (values.length - 1);
  const y = (x) => (h - 4 - ((x - min) / range) * (h - 8)).toFixed(1);
  let d = '';
  values.forEach((x, i) => { if (x != null) d += `${d && values[i - 1] != null ? 'L' : 'M'}${(i * step).toFixed(1)},${y(x)}`; });
  const base = baseline != null ? `<line x1="0" x2="${w}" y1="${y(baseline)}" y2="${y(baseline)}" stroke="#8A8578" stroke-dasharray="3 3" stroke-opacity=".6"/>` : '';
  return `<svg class="chart" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" style="height:${h}px">${base}<path d="${d}" fill="none" stroke="${color}" stroke-width="2" vector-effect="non-scaling-stroke"/></svg>`;
}

function openStrait(id) {
  const s = (S.radar?.straits?.items || []).find((x) => x.id === id);
  if (!s) return;
  const st = straitStatus(s);
  const pct = (now, before) => (now != null && before ? ` (${now >= before ? '+' : ''}${Math.round(((now - before) / before) * 100)}%)` : '');
  showDetail(`<div class="detail-head"><div class="kicker">Estreito</div><h2>${esc(s.name)}</h2>
    <div class="m">IMF PortWatch · dados até ${esc(s.date || '')}</div></div>
    <div class="card${st === 'queda' ? ' alert' : ''}"><h4>🚢 Navios por dia<small>média de 7 dias</small></h4>
      <div class="count" style="font:700 28px var(--mono)">${s.avg7 != null ? Math.round(s.avg7) : '—'}</div>
      <p class="small muted">90 dias antes: ${s.avg90 != null ? Math.round(s.avg90) : '—'}${pct(s.avg7, s.avg90)} · há um ano: ${s.year_ago != null ? Math.round(s.year_ago) : '—'}${pct(s.avg7, s.year_ago)}</p>
      ${sparkSvg(s.spark, '#C9A227', { baseline: s.avg90 })}
      <p class="small muted">Linha tracejada = média de 90 dias antes. ${st === 'queda' ? '<span class="alertline">Tráfego bem abaixo do normal.</span>' : ''}</p></div>
    <div class="card"><h4>Fonte</h4><p class="small">IMF PortWatch (FMI), com dados de satélite dos navios (AIS). O FMI atualiza uma vez por semana, com alguns dias de atraso.</p></div>
    ${s.tag ? `<div class="linkrow"><span class="chip" data-region="${s.tag}">${esc(label(s.tag))}</span></div>` : ''}`);
  const pos = STRAIT_POS[id];
  if (pos) flyCam({ center: pos, zoom: Math.max(map.getZoom(), 5.5), speed: 0.9 });
}

// ---------------------------------------------------------------------------
// 4. Ficha do país (Wikidata + Wikipédia em português), ao clicar num país
// ---------------------------------------------------------------------------
const COUNTRY_TAG = {
  Israel: 'israel', Palestine: 'gaza', Lebanon: 'libano', Iran: 'ira', Yemen: 'iemen', Syria: 'siria', Iraq: 'iraque',
  'United States of America': 'eua', Ukraine: 'ucrania_russia', Russia: 'ucrania_russia', Sudan: 'sudao',
  Egypt: 'egito', Jordan: 'jordania', 'Saudi Arabia': 'arabia', 'United Arab Emirates': 'emirados', Qatar: 'golfo',
  Bahrain: 'golfo', Kuwait: 'golfo', Oman: 'golfo', Turkey: 'turquia', Somalia: 'somalia', Somaliland: 'somalia',
  Cyprus: 'mediterraneo', 'N. Cyprus': 'mediterraneo', Brazil: 'brasil',
};
const COUNTRY_ALIAS = {
  'United States of America': 'United States', 'Dem. Rep. Congo': 'Democratic Republic of the Congo',
  'Central African Rep.': 'Central African Republic', 'Bosnia and Herz.': 'Bosnia and Herzegovina',
  'Dominican Rep.': 'Dominican Republic', 'Eq. Guinea': 'Equatorial Guinea', 'S. Sudan': 'South Sudan',
  'Solomon Is.': 'Solomon Islands', Palestine: 'State of Palestine', Macedonia: 'North Macedonia',
  "Côte d'Ivoire": 'Ivory Coast', eSwatini: 'Eswatini', 'W. Sahara': 'Western Sahara', 'N. Cyprus': 'Northern Cyprus',
  'Falkland Is.': 'Falkland Islands', 'Timor-Leste': 'East Timor', Czechia: 'Czech Republic',
  'Fr. S. Antarctic Lands': 'French Southern and Antarctic Lands', 'Br. Indian Ocean Ter.': 'British Indian Ocean Territory',
};
const countryCache = store.get('argos_countries_v1', {});

async function countryInfo(name) {
  if (countryCache[name]) return countryCache[name];
  const en = (COUNTRY_ALIAS[name] || name).replace(/"/g, '');
  const sparql = `SELECT ?c ?label ?capLabel ?pop ?flag ?hosLabel ?hogLabel ?article WHERE {
    VALUES ?t { wd:Q6256 wd:Q3624078 wd:Q15634554 wd:Q1763527 }
    ?c wdt:P31 ?t; rdfs:label|skos:altLabel "${en}"@en.
    OPTIONAL { ?c rdfs:label ?label FILTER(lang(?label) = "pt") }
    OPTIONAL { ?c wdt:P36 ?cap }
    OPTIONAL { ?c wdt:P1082 ?pop }
    OPTIONAL { ?c wdt:P41 ?flag }
    OPTIONAL { ?c wdt:P35 ?hos }
    OPTIONAL { ?c wdt:P6 ?hog }
    OPTIONAL { ?article schema:about ?c; schema:isPartOf <https://pt.wikipedia.org/> }
    SERVICE wikibase:label { bd:serviceParam wikibase:language "pt,en". }
  } LIMIT 400`;
  const r = await fetch(`https://query.wikidata.org/sparql?format=json&query=${encodeURIComponent(sparql)}`, { headers: { Accept: 'application/sparql-results+json' } });
  if (!r.ok) throw new Error(`Wikidata ${r.status}`);
  const rows = (await r.json()).results.bindings;
  if (!rows.length) throw new Error('não achado');
  const first = rows[0];
  const uniq = (key) => [...new Set(rows.map((x) => x[key]?.value).filter(Boolean))];
  const info = {
    name: first.label?.value || name,
    capital: uniq('capLabel'),
    population: Math.max(0, ...rows.map((x) => Number(x.pop?.value || 0))),
    flag: first.flag?.value || null,
    headOfState: uniq('hosLabel').slice(0, 2),
    headOfGov: uniq('hogLabel').slice(0, 2),
    article: first.article?.value || null,
    extract: '',
  };
  if (info.article) {
    try {
      const title = decodeURIComponent(info.article.split('/wiki/')[1]);
      const s = await fetch(`https://pt.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(title)}`);
      if (s.ok) info.extract = (await s.json()).extract || '';
    } catch { /* sem resumo */ }
  }
  countryCache[name] = info;
  store.set('argos_countries_v1', countryCache);
  return info;
}

function countryNews(names) {
  const terms = names.filter(Boolean).map((n) => wordRe(n));
  return (S.feed?.clusters || []).filter((c) => {
    const text = norm([c.title, TR.get(c.title, c.lang), c.summary].join(' '));
    return terms.some((re) => re.test(text));
  }).slice(0, 15);
}

function powerHtml(tag) {
  const sides = (S.geo?.power || []).filter((p) => p.tag === tag);
  if (!sides.length) return '';
  return sides.map((side) => `<div class="card"><h4>🧭 Quem manda · ${esc(side.title)}<small>até 2025</small></h4>
    ${side.roles.map((r) => {
      const wiki = (r.person && S.geo.wiki?.[r.person]) || (!/[,(]/.test(r.name) ? r.name.replace(/ /g, '_') : null);
      return `<div class="person">${wiki ? `<img alt="" loading="lazy" data-wiki="${esc(wiki)}">` : '<span class="ph"></span>'}
        <div><div class="small muted">${esc(r.role)}</div><div>${esc(r.name)}</div></div></div>`;
    }).join('')}</div>`).join('');
}

/** Fotos dos cargos pelo resumo da Wikipédia (em inglês, que tem mais fotos). */
async function loadWikiPhotos(root) {
  for (const img of root.querySelectorAll('img[data-wiki]')) {
    const title = img.dataset.wiki;
    const key = `argos_wimg_${title}`;
    let src = store.get(key, null);
    if (src == null) {
      try {
        const r = await fetch(`https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(title)}`);
        src = r.ok ? ((await r.json()).thumbnail?.source || '') : '';
      } catch { src = ''; }
      store.set(key, src);
    }
    if (src) img.src = src; else img.replaceWith(Object.assign(document.createElement('span'), { className: 'ph' }));
  }
}

async function openCountry(name, lngLat) {
  S.story = null;
  const tag = COUNTRY_TAG[name];
  showDetail(`<div class="detail-head"><div class="kicker">País</div><h2>${esc(name)}</h2><div class="m">Carregando a ficha…</div></div>`);
  let info = null;
  try { info = await countryInfo(name); } catch { /* sem internet para a Wikidata: mostra o que der */ }
  const ptName = info?.name || name;
  const news = countryNews([ptName, name, COUNTRY_ALIAS[name]]);
  const pop = info?.population ? (info.population >= 1e6 ? `${(info.population / 1e6).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} milhões` : fmt(info.population)) : null;
  showDetail(`
    <div class="detail-head">
      ${info?.flag ? `<div class="flags"><img alt="" src="${esc(info.flag.replace('http://', 'https://'))}?width=80" onerror="this.remove()"></div>` : ''}
      <div class="kicker">País</div><h2>${esc(ptName)}</h2>
      <div class="m">${[info?.capital?.length ? `Capital: ${info.capital.map(esc).join(', ')}` : '', pop ? `População: ~${pop}` : ''].filter(Boolean).join(' · ')}</div>
      ${tag ? `<div style="margin-top:8px"><span class="chip" data-region="${tag}">Região acompanhada: ${esc(label(tag))} →</span></div>` : ''}
    </div>
    ${info && (info.headOfState.length || info.headOfGov.length) ? `<div class="card"><h4>Governo<small>Wikidata</small></h4>
      ${info.headOfState.length ? `<p class="small"><span class="muted">Chefe de Estado:</span> ${info.headOfState.map(esc).join(', ')}</p>` : ''}
      ${info.headOfGov.length ? `<p class="small"><span class="muted">Chefe de governo:</span> ${info.headOfGov.map(esc).join(', ')}</p>` : ''}</div>` : ''}
    ${info?.extract ? `<div class="card"><h4>Resumo<small>Wikipédia</small></h4><p class="small">${esc(info.extract)}</p>
      ${info.article ? `<a class="small" href="${esc(info.article)}" target="_blank" rel="noopener">Ler na Wikipédia →</a>` : ''}</div>` : ''}
    ${!info ? '<div class="card"><p class="small muted">A ficha vem da Wikidata e não carregou agora.</p></div>' : ''}
    ${tag ? powerHtml(tag) : ''}
    <div class="card"><h4>Nas notícias agora<small>${news.length}</small></h4>${news.length ? '' : '<p class="small muted">Nenhuma notícia das últimas 48 h cita este país.</p>'}</div>
    ${news.map(storyRow).join('')}`);
  loadWikiPhotos($('#detail-body'));
  if (lngLat) flyCam({ center: lngLat, zoom: Math.max(map.getZoom(), 3.6), speed: 0.8 });
}

// ---------------------------------------------------------------------------
// 5. Comparar a tensão de regiões em 30 dias
// ---------------------------------------------------------------------------
let compareSel = null;
function openCompare() {
  if (!compareSel) {
    compareSel = Object.entries(S.feed?.regions || {}).sort((a, b) => b[1].tension - a[1].tension).slice(0, 3).map(([t]) => t);
  }
  S.story = null;
  const days = (S.stats?.days || []).slice(-30);
  const w = 380, h = 180;
  const x = (i) => (days.length > 1 ? (i / (days.length - 1)) * w : 0);
  const y = (v) => (h - 6 - (v / 100) * (h - 12)).toFixed(1);
  const lines = compareSel.map((tag, k) => {
    let d = '';
    days.forEach((day, i) => {
      const v = day.tension?.[tag];
      if (v != null) d += `${d ? 'L' : 'M'}${x(i).toFixed(1)},${y(v)}`;
    });
    return d ? `<path d="${d}" fill="none" stroke="${COMPARE_COLORS[k]}" stroke-width="2.2" vector-effect="non-scaling-stroke"/>` : '';
  }).join('');
  const grid = [25, 50, 75].map((v) => `<line x1="0" x2="${w}" y1="${y(v)}" y2="${y(v)}" stroke="${v === 75 ? '#B3122E' : '#2a271f'}" stroke-dasharray="3 3"/>`).join('');
  const chips = Object.keys(S.geo?.labels || {}).map((tag) => {
    const k = compareSel.indexOf(tag);
    return `<button class="chip" data-compare="${tag}" style="${k >= 0 ? `border-color:${COMPARE_COLORS[k]};color:${COMPARE_COLORS[k]}` : ''}">${k >= 0 ? '● ' : ''}${esc(label(tag))}</button>`;
  }).join('');
  showDetail(`<div class="detail-head"><div class="kicker">Comparar</div><h2>Tensão lado a lado</h2>
    <div class="m">Até 4 regiões · ${days.length} dias · linha vermelha = crítica (75)</div></div>
    <div class="card">${days.length > 1 ? `<svg class="chart" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" style="height:${h}px">${grid}${lines}</svg>
      <div class="small muted" style="display:flex;justify-content:space-between"><span>${esc(days[0]?.date || '')}</span><span>${esc(days[days.length - 1]?.date || '')}</span></div>` :
      '<p class="small muted">O histórico enche com os dias.</p>'}</div>
    <div class="filter">${chips}</div>`);
}
function toggleCompare(tag) {
  compareSel = compareSel || [];
  if (compareSel.includes(tag)) compareSel = compareSel.filter((t) => t !== tag);
  else if (compareSel.length < 4) compareSel.push(tag);
  else toast('Até 4 regiões por vez.');
  openCompare();
}

// ---------------------------------------------------------------------------
// 6. Arquivo: a principal de cada dia
// ---------------------------------------------------------------------------
S.archive = null;
async function loadArchive() {
  if (S.archive) return S.archive;
  try {
    const idx = await getJSON(`${BASE}history/index.json`);
    const days = await Promise.all((idx.days || []).slice(0, 30).map((d) => getJSON(`${BASE}history/${d}.json`).catch(() => null)));
    S.archive = days.filter(Boolean);
  } catch {
    S.archive = [];
  }
  return S.archive;
}

function findCluster(id) {
  return S.feed?.clusters.find((c) => c.id === id) || (S.archive || []).map((d) => d.top).find((c) => c.id === id) || null;
}

function archiveList() {
  if (!S.archive) {
    loadArchive().then(() => { if (S.tab === 'archive') renderLeft(); });
    return '<p class="muted" style="padding:14px">Carregando o arquivo…</p>';
  }
  if (!S.archive.length) return '<p class="muted" style="padding:14px">Nada no arquivo ainda.</p>';
  return S.archive.map((d) => {
    const c = d.top;
    const date = new Date(`${d.date}T12:00:00Z`).toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' });
    return `<button class="row${S.story === c.id ? ' sel' : ''}" data-story="${c.id}">
      <div class="badges">${esc(date.toUpperCase())}</div>
      <div class="t">${esc(TR.show(c.title, c.lang))}</div>
      <div class="m">${esc(c.source)} · ${c.sources_count} veículos · ${c.tags.map((t) => esc(label(t))).slice(0, 2).join(' · ')}</div></button>`;
  }).join('');
}

// ---------------------------------------------------------------------------
// 8. Mercados com gráfico e apostas
// ---------------------------------------------------------------------------
function marketsCards() {
  const r = S.radar;
  const parts = [];
  const mk = r?.markets;
  if (mk) {
    parts.push(`<div class="card"><h4>🛢 Termômetro do mercado<small>${rel(mk.updated)}</small></h4>${(mk.items || []).filter((q) => q.price != null).map((q) => {
      const ch = q.change_pct;
      const up = (ch ?? 0) >= 0;
      return `<div class="quote"><div><div class="small">${esc(q.name)}</div>
        <div><b>${Number(q.price).toLocaleString('pt-BR', { maximumFractionDigits: q.digits ?? 2 })}</b> <span class="muted small">${esc(q.unit || '')}</span>
        ${ch != null ? ` <span class="small" style="color:${up ? '#C9A227' : '#8A8578'}">${up ? '▲' : '▼'} ${Math.abs(ch).toFixed(2)}%</span>` : ''}
        ${q.change_week_pct != null ? ` <span class="small muted">· ${q.change_week_pct >= 0 ? '+' : ''}${q.change_week_pct.toFixed(1)}% em 7 dias</span>` : ''}</div></div>
        ${sparkSvg(q.spark, up ? '#C9A227' : '#8A8578', { w: 120, h: 32 })}</div>`;
    }).join('')}<p class="small muted">Petróleo e ouro sobem com medo de guerra; shekel, rublo e hryvnia mostram como o mercado vê Israel, Rússia e Ucrânia.</p></div>`);
  }
  const pr = r?.predictions;
  if (pr?.events?.length) {
    parts.push(`<div class="card"><h4>🎲 O que os apostadores acham<small>Polymarket</small></h4>${pr.events.slice(0, 6).map((e) => {
      const m = [...(e.markets || [])].sort((a, b) => b.prob - a.prob)[0];
      const ch = m?.change != null && Math.abs(m.change) >= 0.01 ? ` <span class="small" style="color:${m.change > 0 ? '#C9A227' : '#8A8578'}">${m.change > 0 ? '+' : ''}${Math.round(m.change * 100)} em 24 h</span>` : '';
      return `<a class="art" href="${esc(e.url)}" target="_blank" rel="noopener"><div class="t small">${esc(TR.show(e.title, 'en'))}</div>
        ${m ? `<div class="m">${esc(m.label === e.title ? 'Sim' : TR.show(m.label, 'en'))}: <b style="color:var(--gold)">${Math.round(m.prob * 100)}%</b>${ch}</div>
        <div class="gauge" style="height:4px;margin:4px 0 0"><i style="width:${Math.round(m.prob * 100)}%;background:var(--gold)"></i></div>` : ''}</a>`;
    }).join('')}<p class="small muted">Apostas com dinheiro real: não são previsões oficiais e podem ser manipuladas.</p></div>`);
  }
  return parts.join('');
}

// ---------------------------------------------------------------------------
// 9. Imagem do globo para compartilhar (estilo dossiê do app)
// ---------------------------------------------------------------------------
function captureMap() {
  return new Promise((resolve, reject) => {
    map.once('render', () => {
      try { resolve(map.getCanvas().toDataURL('image/png')); } catch (e) { reject(e); }
    });
    map.triggerRepaint();
  });
}


async function shareImage() {
  toast('Montando a imagem…');
  let data;
  try {
    data = await captureMap();
  } catch {
    // Fundo de outro site que não permite cópia: tira a foto só com os contornos.
    const base = S.base;
    setBase('none');
    await new Promise((r) => setTimeout(r, 400));
    data = await captureMap().catch(() => null);
    setBase(base);
  }
  if (!data) { toast('Não foi possível gerar a imagem.'); return; }
  const img = new Image();
  img.src = data;
  await img.decode();
  const W = 1600, H = 1000, top = 110, bottom = 70;
  const cv = document.createElement('canvas');
  cv.width = W; cv.height = H;
  const g = cv.getContext('2d');
  g.fillStyle = '#050505';
  g.fillRect(0, 0, W, H);
  // Globo recortado no meio.
  const mc = map.getCanvas();
  const scale = Math.max(W / mc.width, (H - top - bottom) / mc.height);
  const dw = mc.width * scale, dh = mc.height * scale;
  const ox = (W - dw) / 2, oy = top + (H - top - bottom - dh) / 2;
  g.save();
  g.beginPath(); g.rect(0, top, W, H - top - bottom); g.clip();
  g.drawImage(img, ox, oy, dw, dh);
  // Regiões (no site são elementos HTML, então são desenhadas à parte).
  const ratio = dw / mc.clientWidth;
  for (const [tag, p] of Object.entries(S.geo?.points || {})) {
    if (!visibleOnGlobe(p[0], p[1])) continue;
    const r = S.feed?.regions?.[tag];
    if (!r) continue;
    const xy = map.project(p);
    const x = ox + xy.x * ratio, y = oy + xy.y * ratio;
    g.fillStyle = LEVEL_COLOR[r.level];
    g.beginPath(); g.arc(x, y, 8 + r.tension / 12, 0, Math.PI * 2); g.fill();
    g.strokeStyle = '#050505'; g.lineWidth = 2; g.stroke();
    g.font = '600 18px system-ui, sans-serif';
    g.fillStyle = '#E8E2D0'; g.textAlign = 'center';
    g.shadowColor = '#000'; g.shadowBlur = 6;
    g.fillText(`${label(tag)} ${r.tension}`, x, y + 30);
    g.shadowBlur = 0;
  }
  g.restore();
  // Cabeçalho: ARGOS, relógio e data.
  g.fillStyle = '#C9A227';
  g.font = '700 44px "Iowan Old Style", Palatino, Georgia, serif';
  g.textAlign = 'left';
  g.fillText('A R G O S', 48, 72);
  const clock = S.feed?.global;
  if (clock) {
    g.textAlign = 'right';
    g.fillStyle = LEVEL_COLOR[clock.level];
    g.font = '800 52px ui-monospace, Consolas, monospace';
    g.fillText(String(clock.index), W - 48, 74);
    g.fillStyle = '#8A8578';
    g.font = '600 16px system-ui, sans-serif';
    g.fillText(`RELÓGIO DO ARGOS · ${clock.level.toUpperCase()} · puxado por ${label(clock.leader)}`, W - 130, 60);
  }
  g.strokeStyle = '#2a271f'; g.lineWidth = 2;
  g.beginPath(); g.moveTo(0, top - 12); g.lineTo(W, top - 12); g.moveTo(0, H - bottom + 12); g.lineTo(W, H - bottom + 12); g.stroke();
  g.fillStyle = '#8A8578';
  g.font = '16px ui-monospace, Consolas, monospace';
  g.textAlign = 'left';
  const now = new Date();
  g.fillText(`DOSSIÊ Nº ${now.toISOString().slice(0, 10).replace(/-/g, '')}-${String(now.getUTCHours()).padStart(2, '0')}${String(now.getUTCMinutes()).padStart(2, '0')} · ${now.toLocaleString('pt-BR')}`, 48, H - 28);
  g.textAlign = 'right';
  g.fillText('abugdn.github.io/Argos/painel', W - 48, H - 28);
  // Carimbo.
  g.save();
  g.translate(W - 230, H - 150); g.rotate(-0.18);
  g.strokeStyle = 'rgba(179,18,46,.85)'; g.lineWidth = 4;
  g.strokeRect(-150, -34, 300, 68);
  g.fillStyle = 'rgba(179,18,46,.85)'; g.font = '800 34px ui-monospace, Consolas, monospace'; g.textAlign = 'center';
  g.fillText('CONFIDENCIAL', 0, 12);
  g.restore();

  const blob = await new Promise((r) => cv.toBlob(r, 'image/png'));
  const file = new File([blob], `argos-${now.toISOString().slice(0, 16).replace(/[:T]/g, '-')}.png`, { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], title: 'Argos' }); return; } catch { /* cancelado: baixa */ }
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = file.name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  toast('Imagem salva.');
}

// ---------------------------------------------------------------------------
// 7. Atalhos de teclado
// ---------------------------------------------------------------------------
const SHORTCUTS = [
  ['/', 'buscar'], ['↑ ↓ ou J K', 'anterior / próxima da lista'], ['1 a 9', 'regiões mais tensas'],
  ['R N G A', 'abas Regiões, Notícias, Radar, Arquivo'], ['C', 'comparar regiões'], ['L', 'camadas'],
  ['D', 'dia e noite'], ['F', 'tela cheia'], ['I', 'imagem para compartilhar'], ['Esc', 'fechar'], ['?', 'esta ajuda'],
];
function openHelp() {
  showDetail(`<div class="detail-head"><div class="kicker">Atalhos</div><h2>Teclado</h2></div>
    <div class="card">${SHORTCUTS.map(([k, d]) => `<p class="small"><kbd>${esc(k)}</kbd> ${esc(d)}</p>`).join('')}</div>`);
}

function switchTab(tab) {
  S.tab = tab;
  document.querySelectorAll('.tabs button').forEach((b) => b.classList.toggle('on', b.dataset.tab === S.tab));
  renderLeft();
}

function moveInList(dir) {
  const rows = [...document.querySelectorAll('#left-body [data-story], #left-body [data-region].row')];
  if (!rows.length) return;
  const i = rows.findIndex((r) => r.classList.contains('sel'));
  const next = rows[Math.max(0, Math.min(rows.length - 1, i + dir))] || rows[0];
  next.click();
  next.scrollIntoView({ block: 'nearest' });
}

document.addEventListener('keydown', (e) => {
  if (e.target.closest('input, textarea') || e.ctrlKey || e.metaKey || e.altKey) return;
  const k = e.key;
  if (k === 'ArrowDown' || k === 'j') { e.preventDefault(); moveInList(1); }
  else if (k === 'ArrowUp' || k === 'k') { e.preventDefault(); moveInList(-1); }
  else if (/^[1-9]$/.test(k)) {
    const regions = Object.entries(S.feed?.regions || {}).sort((a, b) => b[1].tension - a[1].tension);
    const r = regions[Number(k) - 1];
    if (r) openRegion(r[0], true);
  }
  else if (k === 'r') switchTab('regions');
  else if (k === 'n') switchTab('news');
  else if (k === 'g') switchTab('radar');
  else if (k === 'a') switchTab('archive');
  else if (k === 'c') openCompare();
  else if (k === 'l') $('#btn-layers').click();
  else if (k === 'd') setLayer('night', !S.layers.night);
  else if (k === 'f') { if (document.fullscreenElement) document.exitFullscreen(); else document.documentElement.requestFullscreen?.(); }
  else if (k === 'i') shareImage();
  else if (k === '?') openHelp();
});
