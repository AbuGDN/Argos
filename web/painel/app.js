/* Argos · Painel: o globo com as mesmas informações do app, lidas dos JSON públicos do site.
 * Sem servidor: feed.json, radar.json, frontline.json e stats/ vêm da pasta de cima (gh-pages);
 * geo.json (regiões, cidades, alcances, contexto) é gerado pelo backend a partir dos arquivos do app. */
'use strict';

const BASE = '../';
const LEVEL_COLOR = { baixa: '#6E8B6A', moderada: '#C9A227', alta: '#C8662B', 'crítica': '#B3122E' };
const INTERNET_STATUS = { normal: 'normal', queda: 'queda', apagao: 'apagão', sem_dados: 'sem dados' };
const AIRSPACE_STATUS = { normal: 'normal', reduzido: 'reduzido', fechado: 'fechado', coletando: 'aprendendo o normal', pouco_trafego: 'pouco tráfego', sem_dados: 'sem dados' };
const LANG_NAMES = { en: 'inglês', he: 'hebraico', ar: 'árabe' };
const MIL_ICONS = { reabastecedor: '⛽', radar: '📡', espionagem: '🛰', bombardeiro: '💣', transporte: '📦', caca: '✈', outro: '•' };

const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const norm = (s) => String(s ?? '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
const fmt = (n) => Number(n ?? 0).toLocaleString('pt-BR');
const levelOf = (v) => (v >= 75 ? 'crítica' : v >= 50 ? 'alta' : v >= 25 ? 'moderada' : 'baixa');
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* sem espaço ou bloqueado */ } },
};

function rel(iso) {
  const t = Date.parse(iso);
  if (!t) return '';
  const m = Math.round((Date.now() - t) / 60000);
  if (m < 1) return 'agora';
  if (m < 60) return `há ${m} min`;
  if (m < 1440) return `há ${Math.floor(m / 60)} h`;
  return new Date(t).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
}
function dayClock(iso) {
  const d = new Date(iso);
  if (isNaN(d)) return '';
  const today = new Date().toDateString() === d.toDateString();
  const hm = d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  return today ? hm : `${d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })} ${hm}`;
}

const S = {
  feed: null, radar: null, geo: null, stats: null, front: null,
  region: null, story: null, tab: 'regions',
  layers: {
    regions: true, cities: true, night: true, arcs: true, straits: true,
    fires: false, aircraft: false, carriers: false, front: false, ranges: false,
    ...store.get('argos_layers', {}),
  },
  base: store.get('argos_base', 'dark'),
  rotate: store.get('argos_rotate', true),
  cityHits: new Map(),
};

// ---------------------------------------------------------------------------
// Tradução: a API de tradução do próprio navegador (Chrome 138+), no computador, sem servidor.
// ---------------------------------------------------------------------------
const TR = {
  cache: store.get('argos_tr_v1', {}),
  engines: {},
  supported: typeof self !== 'undefined' && 'Translator' in self,
  busy: false,
  get(text, lang) { return !text || lang === 'pt' ? text : this.cache[`${lang}|${text}`] || null; },
  show(text, lang) { return this.get(text, lang) || text; },
  save() {
    const keys = Object.keys(this.cache);
    if (keys.length > 4000) keys.slice(0, keys.length - 3000).forEach((k) => delete this.cache[k]);
    store.set('argos_tr_v1', this.cache);
  },
  async engine(lang) {
    if (this.engines[lang] !== undefined) return this.engines[lang];
    try {
      const av = await self.Translator.availability({ sourceLanguage: lang, targetLanguage: 'pt' });
      if (av === 'unavailable') return (this.engines[lang] = null);
      this.engines[lang] = await self.Translator.create({ sourceLanguage: lang, targetLanguage: 'pt' });
    } catch (e) {
      // "downloadable" pede um clique da pessoa antes de baixar o modelo.
      return null;
    }
    return this.engines[lang];
  },
  async run() {
    if (!this.supported || this.busy || !S.feed) return;
    this.busy = true;
    let done = 0;
    const todo = [];
    for (const c of S.feed.clusters) {
      if (c.lang !== 'pt') {
        todo.push([c.title, c.lang]);
        if (c.summary) todo.push([c.summary, c.lang]);
      }
    }
    for (const [text, lang] of todo) {
      if (this.get(text, lang)) continue;
      const eng = await this.engine(lang);
      if (!eng) continue;
      try {
        this.cache[`${lang}|${text}`] = await eng.translate(text);
        if (++done % 15 === 0) { this.save(); renderLeft(); }
      } catch { /* texto que o modelo recusou: fica no original */ }
    }
    this.busy = false;
    if (done) { this.save(); renderAll(); }
  },
};

async function setupTranslation() {
  const banner = $('#tr-banner');
  const foreign = S.feed.clusters.filter((c) => c.lang !== 'pt').length;
  if (!foreign) return;
  if (!TR.supported) {
    banner.hidden = false;
    banner.innerHTML = `${foreign} notícias estão em inglês, hebraico ou árabe e aparecem no original. ` +
      'No Google Chrome (versão 138 ou mais nova) o painel traduz sozinho, no próprio computador. ' +
      'Em cada notícia há um botão para abrir a tradução do site.';
    return;
  }
  const langs = [...new Set(S.feed.clusters.map((c) => c.lang).filter((l) => l !== 'pt'))];
  const states = await Promise.all(langs.map((l) => self.Translator.availability({ sourceLanguage: l, targetLanguage: 'pt' }).catch(() => 'unavailable')));
  if (states.every((s) => s === 'available')) { TR.run(); return; }
  if (states.every((s) => s === 'unavailable')) return;
  banner.hidden = false;
  banner.innerHTML = 'O Chrome pode traduzir os títulos para o português no próprio computador ' +
    '(baixa os modelos de tradução uma vez).<br><button class="btn" id="tr-go" type="button">Traduzir para português</button>';
  $('#tr-go').onclick = async () => {
    banner.innerHTML = 'Baixando os modelos de tradução…';
    for (const l of langs) await TR.engine(l);
    banner.hidden = true;
    TR.run();
  };
}

// ---------------------------------------------------------------------------
// Dados
// ---------------------------------------------------------------------------
async function getJSON(path) {
  const r = await fetch(`${path}${path.includes('?') ? '&' : '?'}t=${Math.floor(Date.now() / 120000)}`);
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
  return r.json();
}

async function load() {
  const [feed, radar, geo, stats] = await Promise.allSettled([
    getJSON(`${BASE}feed.json`), getJSON(`${BASE}radar.json`), getJSON('geo.json'), getJSON(`${BASE}stats/daily.json`),
  ]);
  if (feed.status === 'fulfilled') S.feed = feed.value;
  if (radar.status === 'fulfilled') S.radar = radar.value;
  if (geo.status === 'fulfilled') S.geo = geo.value;
  if (stats.status === 'fulfilled') S.stats = stats.value;
  if (!S.feed) { toast('Não foi possível baixar as notícias. Tente de novo em instantes.'); return; }
  indexCities();
}

const label = (tag) => S.geo?.labels?.[tag] || tag;
const title = (c) => TR.show(c.title, c.lang);

/** Cidades citadas em cada notícia (título, resumo, tradução e títulos de todos os veículos). */
const termRegex = new Map();
function wordRe(term) {
  let re = termRegex.get(term);
  if (!re) {
    const t = norm(term).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    re = new RegExp(`(^|[^\\p{L}\\d])${t}($|[^\\p{L}\\d])`, 'u');
    termRegex.set(term, re);
  }
  return re;
}
function indexCities() {
  S.cityHits = new Map();
  const cities = S.geo?.cities || [];
  for (const c of S.feed.clusters) {
    const text = norm([c.title, c.summary, TR.get(c.title, c.lang), ...c.articles.map((a) => a.title)].join(' \n '));
    for (const city of cities) {
      if (city.terms.some((t) => wordRe(t).test(text))) {
        if (!S.cityHits.has(city.name)) S.cityHits.set(city.name, []);
        S.cityHits.get(city.name).push(c);
      }
    }
  }
}

function radarAlerts(r) {
  if (!r) return [];
  const out = [];
  (r.internet?.countries || []).filter((c) => c.status === 'apagao' || c.status === 'queda').forEach((c) =>
    out.push({ text: `🌐 ${c.status === 'apagao' ? 'Apagão' : 'Queda'} de internet: ${c.name} (${Math.round(c.ratio * 100)}% do normal)`, tag: c.tag }));
  (r.airspace?.zones || []).filter((z) => z.status === 'fechado' || z.status === 'reduzido').forEach((z) =>
    out.push({ text: `✈ Espaço aéreo ${z.status}: ${z.name}`, tag: z.tag }));
  (r.fires?.zones || []).filter((z) => z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline).forEach((z) =>
    out.push({ text: `🔥 Focos de calor acima do normal: ${z.name} (${z.count} em 24 h)`, tag: z.tag, layer: 'fires' }));
  (r.straits?.items || []).filter((s) => s.avg7 != null && s.avg90 != null && s.avg90 > 5 && s.avg7 < s.avg90 * 0.6).forEach((s) =>
    out.push({ text: `🚢 Tráfego em queda: ${s.name}`, tag: s.tag }));
  (r.military?.zones || []).filter((z) => z.unusual).forEach((z) =>
    out.push({ text: `✈ Aviões militares acima do normal: ${z.name}`, tag: z.tag, layer: 'aircraft' }));
  return out;
}

function regionRadarLines(tag) {
  const r = S.radar;
  if (!r) return [];
  const lines = [];
  (r.internet?.countries || []).filter((c) => c.tag === tag && c.status !== 'sem_dados').forEach((c) =>
    lines.push([`🌐 Internet (${c.name}): ${INTERNET_STATUS[c.status] || c.status} · ${Math.round(c.ratio * 100)}% do normal`, c.status !== 'normal']));
  (r.airspace?.zones || []).filter((z) => z.tag === tag && z.status !== 'sem_dados').forEach((z) =>
    lines.push([`✈ Espaço aéreo (${z.name}): ${AIRSPACE_STATUS[z.status] || z.status} · ${z.flights} aviões no ar`, z.status === 'fechado' || z.status === 'reduzido']));
  (r.fires?.zones || []).filter((z) => z.tag === tag && !z.error).forEach((z) => {
    const high = z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline;
    lines.push([`🔥 Focos de calor (${z.name}): ${z.count} em 24 h${z.baseline != null ? ` · média ${Math.round(z.baseline)}` : ''}`, high]);
  });
  (r.straits?.items || []).filter((s) => s.tag === tag && s.avg7 != null).forEach((s) => {
    const low = s.avg90 != null && s.avg90 > 5 && s.avg7 < s.avg90 * 0.6;
    lines.push([`🚢 ${s.name}: ${Math.round(s.avg7)} navios por dia${s.avg90 != null ? ` · antes ${Math.round(s.avg90)}` : ''}`, low]);
  });
  const cw = r.crisiswatch;
  if (cw) {
    const month = cw.month || 'este mês';
    if ((cw.deteriorated || []).some((c) => c.tag === tag)) lines.push([`📉 CrisisWatch: piorou em ${month}`, true]);
    if ((cw.improved || []).some((c) => c.tag === tag)) lines.push([`📈 CrisisWatch: melhorou em ${month}`, false]);
    if ((cw.risk || []).some((c) => c.tag === tag)) lines.push([`⚠ CrisisWatch: alerta de risco de conflito (${month})`, true]);
  }
  return lines;
}

// ---------------------------------------------------------------------------
// Globo
// ---------------------------------------------------------------------------
const TILE = {
  // Mapas da Esri de uso livre, sem chave (o CARTO passou a exigir chave em 2026).
  dark: ['https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}'],
  labels: ['https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}'],
  sat: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'],
};

const map = new maplibregl.Map({
  container: 'map',
  // Começa longe: a abertura (motion.js) desce até o Oriente Médio.
  center: [-40, 18],
  zoom: 0.9,
  minZoom: 0.8,
  maxZoom: 17,
  attributionControl: { compact: true },
  style: {
    version: 8,
    projection: { type: 'globe' },
    sky: { 'atmosphere-blend': ['interpolate', ['linear'], ['zoom'], 0, 1, 5, 1, 7, 0] },
    sources: {
      countries: { type: 'geojson', data: 'countries.geojson', attribution: 'Natural Earth' },
      borders: { type: 'geojson', data: 'borders.geojson' },
      dark: { type: 'raster', tiles: TILE.dark, tileSize: 256, maxzoom: 16, attribution: 'Mapa © Esri, HERE, Garmin, © OpenStreetMap' },
      labels: { type: 'raster', tiles: TILE.labels, tileSize: 256, maxzoom: 16 },
      sat: { type: 'raster', tiles: TILE.sat, tileSize: 256, maxzoom: 19, attribution: 'Imagens © Esri, Maxar, Earthstar Geographics' },
    },
    layers: [
      { id: 'ocean', type: 'background', paint: { 'background-color': '#07090c' } },
      { id: 'land', type: 'fill', source: 'countries', paint: { 'fill-color': '#12110d' } },
      { id: 'dark', type: 'raster', source: 'dark', paint: { 'raster-opacity': 0.92 }, layout: { visibility: 'none' } },
      { id: 'sat', type: 'raster', source: 'sat', paint: { 'raster-saturation': -0.35, 'raster-brightness-max': 0.8 }, layout: { visibility: 'none' } },
      { id: 'labels', type: 'raster', source: 'labels', minzoom: 4, paint: { 'raster-opacity': 0.85 }, layout: { visibility: 'none' } },
      { id: 'borders', type: 'line', source: 'borders', paint: { 'line-color': '#C9A227', 'line-opacity': ['interpolate', ['linear'], ['zoom'], 1, 0.45, 6, 0.25], 'line-width': 0.7 } },
    ],
  },
});
map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'bottom-right');

function setBase(base) {
  S.base = base;
  store.set('argos_base', base);
  if (!map.isStyleLoaded()) return;
  map.setLayoutProperty('dark', 'visibility', base === 'dark' ? 'visible' : 'none');
  map.setLayoutProperty('sat', 'visibility', base === 'sat' ? 'visible' : 'none');
  map.setLayoutProperty('labels', 'visibility', base === 'none' ? 'none' : 'visible');
}

/** Círculo geodésico (alcance de míssil) como polígono. */
function circle(lon, lat, km, steps = 96) {
  const R = 6371, d = km / R, la = (lat * Math.PI) / 180, lo = (lon * Math.PI) / 180;
  const ring = [];
  for (let i = 0; i <= steps; i++) {
    const b = (2 * Math.PI * i) / steps;
    const la2 = Math.asin(Math.sin(la) * Math.cos(d) + Math.cos(la) * Math.sin(d) * Math.cos(b));
    const lo2 = lo + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(la), Math.cos(d) - Math.sin(la) * Math.sin(la2));
    ring.push([(lo2 * 180) / Math.PI, (la2 * 180) / Math.PI]);
  }
  return ring;
}

const fc = (features) => ({ type: 'FeatureCollection', features });
const pt = (lon, lat, props) => ({ type: 'Feature', geometry: { type: 'Point', coordinates: [lon, lat] }, properties: props });

function addDataLayers() {
  const empty = fc([]);
  for (const id of ['night', 'arcs', 'straits', 'ranges', 'front', 'fires', 'aircraft', 'cities']) map.addSource(id, { type: 'geojson', data: empty });
  map.addLayer({ id: 'night', type: 'fill', source: 'night', paint: { 'fill-color': '#000000', 'fill-opacity': 0.42 } });
  map.addLayer({ id: 'night-edge', type: 'line', source: 'night', paint: { 'line-color': '#C9A227', 'line-opacity': 0.18, 'line-width': 6, 'line-blur': 6 } });
  map.addLayer({ id: 'ranges-fill', type: 'fill', source: 'ranges', paint: { 'fill-color': ['case', ['get', 'defense'], '#6E8B6A', '#B3122E'], 'fill-opacity': 0.06 } });
  map.addLayer({ id: 'ranges-line', type: 'line', source: 'ranges', paint: { 'line-color': ['case', ['get', 'defense'], '#6E8B6A', '#B3122E'], 'line-width': 1.4, 'line-dasharray': [3, 2] } });
  map.addLayer({ id: 'front-fill', type: 'fill', source: 'front', paint: { 'fill-color': ['case', ['==', ['get', 'kind'], 'grey'], '#8A8578', '#B3122E'], 'fill-opacity': 0.35 } });
  map.addLayer({ id: 'front-line', type: 'line', source: 'front', paint: { 'line-color': '#B3122E', 'line-width': 1 } });
  map.addLayer({ id: 'fires', type: 'circle', source: 'fires', paint: { 'circle-color': '#FF7A1A', 'circle-radius': ['interpolate', ['linear'], ['zoom'], 2, 1.6, 8, 4], 'circle-opacity': 0.85, 'circle-blur': 0.3 } });
  map.addLayer({
    id: 'cities', type: 'circle', source: 'cities',
    paint: {
      'circle-color': '#E8E2D0', 'circle-opacity': 0.85, 'circle-stroke-color': '#050505', 'circle-stroke-width': 1,
      'circle-radius': ['interpolate', ['linear'], ['zoom'], 2, ['+', 1.5, ['*', 0.6, ['sqrt', ['get', 'n']]]], 7, ['+', 4, ['*', 1.6, ['sqrt', ['get', 'n']]]]],
    },
  });
  map.addLayer({ id: 'arcs-base', type: 'line', source: 'arcs', layout: { 'line-cap': 'round' }, paint: { 'line-color': '#C9A227', 'line-opacity': 0.22, 'line-width': ['+', 1, ['*', 1.3, ['ln', ['get', 'n']]]] } });
  map.addLayer({ id: 'arcs-flow', type: 'line', source: 'arcs', paint: { 'line-color': '#E8C766', 'line-opacity': 0.85, 'line-width': ['+', 1, ['*', 1.3, ['ln', ['get', 'n']]]], 'line-dasharray': [0, 4, 3] } });
  map.addLayer({ id: 'straits-halo', type: 'circle', source: 'straits', paint: { 'circle-radius': 16, 'circle-color': ['match', ['get', 'status'], 'queda', '#B3122E', 'normal', '#6E8B6A', '#8A8578'], 'circle-opacity': 0.18, 'circle-blur': 0.6 } });
  map.addLayer({ id: 'straits', type: 'circle', source: 'straits', paint: { 'circle-radius': 6, 'circle-color': ['match', ['get', 'status'], 'queda', '#B3122E', 'normal', '#6E8B6A', '#8A8578'], 'circle-stroke-color': '#E8E2D0', 'circle-stroke-width': 1.5 } });
  map.addLayer({ id: 'aircraft', type: 'circle', source: 'aircraft', paint: { 'circle-color': '#C9A227', 'circle-radius': 4, 'circle-stroke-color': '#050505', 'circle-stroke-width': 1.5 } });

  const popup = new maplibregl.Popup({ closeButton: false, closeOnClick: false, offset: 10 });
  const hover = (layer, html) => {
    map.on('mouseenter', layer, (e) => { map.getCanvas().style.cursor = 'pointer'; popup.setLngLat(e.lngLat).setHTML(html(e.features[0].properties)).addTo(map); });
    map.on('mousemove', layer, (e) => popup.setLngLat(e.lngLat).setHTML(html(e.features[0].properties)));
    map.on('mouseleave', layer, () => { map.getCanvas().style.cursor = ''; popup.remove(); });
  };
  hover('cities', (p) => `<b>${esc(p.name)}</b><br>${p.n} ${p.n == 1 ? 'notícia cita' : 'notícias citam'} · toque para ver`);
  hover('aircraft', (p) => `<b>${esc(MIL_ICONS[p.category] || '•')} ${esc(p.label)}</b><br>${esc(p.type)} ${esc(p.callsign)}<br><span class="muted">${p.alt ? `${fmt(p.alt)} pés · ` : ''}adsb.lol</span>`);
  hover('fires', (p) => `🔥 Foco de calor · ${esc(p.zone)}<br><span class="muted">NASA FIRMS, últimas 24 h</span>`);
  hover('ranges-line', (p) => `<b>${esc(p.label)}</b><br>${esc(p.detail)}`);
  hover('arcs-base', (p) => `<b>${esc(p.name)}</b><br>${p.n} notícias das últimas 36 h citam as duas · toque para ver`);
  hover('straits', (p) => `🚢 <b>${esc(p.name)}</b><br>${p.avg7 >= 0 ? `${Math.round(p.avg7)} navios por dia` : 'sem dados'}${p.avg90 >= 0 ? ` · antes ${Math.round(p.avg90)}` : ''}${p.status === 'queda' ? '<br><span class="alertline">tráfego em queda</span>' : ''}`);
  map.on('click', 'arcs-base', (e) => openPair(e.features[0].properties.a, e.features[0].properties.b));
  map.on('click', 'straits', (e) => openStrait(e.features[0].properties.id));
  map.on('click', 'cities', (e) => openCity(e.features[0].properties.name));
  // Clique num país sem nada em cima: ficha do país.
  const interactive = ['cities', 'aircraft', 'fires', 'straits', 'arcs-base', 'ranges-line'];
  map.on('click', (e) => {
    if (map.queryRenderedFeatures(e.point, { layers: interactive }).length) return;
    const land = map.queryRenderedFeatures(e.point, { layers: ['land'] });
    if (land.length) openCountry(land[0].properties.name, e.lngLat);
  });
}

// Marcadores das regiões (HTML: anel de sonar, número e nome).
const markers = { regions: new Map(), carriers: [] };

function renderRegionMarkers() {
  const on = S.layers.regions;
  const regions = S.feed?.regions || {};
  const pts = S.geo?.points || {};
  const counts = {};
  for (const c of S.feed?.clusters || []) for (const t of c.tags) counts[t] = (counts[t] || 0) + 1;
  for (const [tag, m] of markers.regions) if (!on || !pts[tag]) { m.remove(); markers.regions.delete(tag); }
  if (!on) return;
  for (const [tag, lnglat] of Object.entries(pts)) {
    const r = regions[tag] || { tension: 0, level: 'baixa' };
    const n = counts[tag] || 0;
    const alert = r.spike || r.level === 'crítica' || (r.signals || []).length > 0;
    let m = markers.regions.get(tag);
    if (!m) {
      const el = document.createElement('div');
      el.className = 'rm';
      el.innerHTML = '<span class="ring"></span><span class="dot"></span><span class="lbl"></span>';
      el.addEventListener('click', (ev) => { ev.stopPropagation(); openRegion(tag, true); });
      m = new maplibregl.Marker({ element: el, anchor: 'center' }).setLngLat(lnglat).addTo(map);
      markers.regions.set(tag, m);
    }
    const el = m.getElement();
    const size = Math.round(10 + Math.min(22, Math.sqrt(n) * 4));
    el.style.color = LEVEL_COLOR[r.level] || LEVEL_COLOR.baixa;
    el.classList.toggle('pulse', alert);
    // Com o globo afastado, só as regiões mais tensas mostram o nome (o Levante fica apertado).
    el.classList.toggle('minor', !alert && (r.tension || 0) < 30);
    el.classList.toggle('sel', S.region === tag);
    const dot = el.querySelector('.dot');
    dot.style.width = dot.style.height = `${size}px`;
    el.style.width = el.style.height = `${size}px`;
    el.querySelector('.lbl').innerHTML = `${esc(label(tag))}<b>${r.tension ?? 0}</b>`;
    el.title = `${label(tag)} · tensão ${r.tension} (${r.level}) · ${n} histórias`;
  }
}

function renderCarriers() {
  markers.carriers.forEach((m) => m.remove());
  markers.carriers = [];
  if (!S.layers.carriers) return;
  for (const s of S.radar?.carriers?.ships || []) {
    const el = document.createElement('div');
    el.className = 'ship';
    el.textContent = '⚓';
    el.title = `${s.name} · ${s.place} · ${s.status}`;
    const pop = new maplibregl.Popup({ offset: 12, closeButton: false }).setHTML(
      `<b>${esc(s.name)}</b> (${esc(s.hull)})<br>${esc(s.place)} · ${esc(s.status)}<br><span class="muted">Posição aproximada · USNI News</span>`);
    markers.carriers.push(new maplibregl.Marker({ element: el }).setLngLat([s.lon, s.lat]).setPopup(pop).addTo(map));
  }
}

async function renderLayers() {
  if (!map.getSource('cities')) return;
  const L = S.layers;
  // Cidades
  const cities = [];
  if (L.cities) {
    for (const city of S.geo?.cities || []) {
      const n = S.cityHits.get(city.name)?.length || 0;
      if (n) cities.push(pt(city.lon, city.lat, { name: city.name, n }));
    }
  }
  map.getSource('cities').setData(fc(cities));
  // Focos de calor
  const fires = [];
  if (L.fires) for (const z of S.radar?.fires?.zones || []) for (const p of z.points || []) fires.push(pt(p[1] ?? p.lon, p[0] ?? p.lat, { zone: z.name, seed: Math.random() }));
  map.getSource('fires').setData(fc(fires));
  // Aviões militares
  const planes = [];
  const labels = S.radar?.military?.labels || {};
  if (L.aircraft) {
    for (const z of S.radar?.military?.zones || []) {
      for (const a of z.aircraft || []) {
        planes.push(pt(a.lon, a.lat, { callsign: a.callsign || a.hex, type: a.type, category: a.category, label: labels[a.category] || a.category, alt: a.alt || 0 }));
      }
    }
  }
  map.getSource('aircraft').setData(fc(planes));
  // Alcances
  const rings = [];
  if (L.ranges) {
    for (const r of S.geo?.ranges || []) {
      rings.push({ type: 'Feature', geometry: { type: 'Polygon', coordinates: [circle(r.lon, r.lat, r.km)] }, properties: { label: r.label, detail: r.detail, defense: r.defense } });
    }
  }
  map.getSource('ranges').setData(fc(rings));
  // Linha de frente (arquivo à parte, baixado só quando a camada liga)
  let front = [];
  if (L.front) {
    if (!S.front) { try { S.front = await getJSON(`${BASE}frontline.json`); } catch { toast('Linha de frente indisponível agora.'); } }
    const ring = (poly) => poly.map((r) => r.map(([lat, lon]) => [lon, lat]));
    for (const kind of ['occupied', 'grey']) {
      for (const poly of S.front?.[kind] || []) front.push({ type: 'Feature', geometry: { type: 'Polygon', coordinates: ring(poly) }, properties: { kind } });
    }
  }
  map.getSource('front').setData(fc(front));
  renderRegionMarkers();
  renderCarriers();
  renderNight();
  renderArcs();
  renderStraits();
  hideBackside();
}

const markFar = () => document.body.classList.toggle('far', map.getZoom() < 3.4);
map.on('zoom', markFar);
markFar();

// Gira devagar quando ninguém mexe (e para ao primeiro toque).
let idleSince = Date.now();
['mousedown', 'wheel', 'touchstart', 'keydown'].forEach((ev) => window.addEventListener(ev, () => { idleSince = Date.now(); }, { passive: true }));
function spin() {
  if (S.rotate && !S.region && !S.story && Date.now() - idleSince > 20000 && !map.isMoving() && map.getZoom() < 4) {
    const c = map.getCenter();
    map.jumpTo({ center: [c.lng + 0.04, c.lat] });
  }
  requestAnimationFrame(spin);
}

// ---------------------------------------------------------------------------
// Painel da esquerda
// ---------------------------------------------------------------------------
function renderLeft() {
  const body = $('#left-body');
  if (!S.feed) { body.innerHTML = '<p class="muted" style="padding:14px">Carregando…</p>'; return; }
  const alerts = radarAlerts(S.radar);
  document.querySelector('[data-tab="radar"]').innerHTML = `Radar${alerts.length ? ' <span class="dot">●</span>' : ''}`;
  if (S.tab === 'regions') body.innerHTML = regionsList();
  else if (S.tab === 'news') body.innerHTML = newsList();
  else if (S.tab === 'archive') body.innerHTML = archiveList();
  else body.innerHTML = radarList(alerts);
}

function regionsList() {
  const regions = Object.entries(S.feed.regions || {}).sort((a, b) => b[1].tension - a[1].tension);
  return '<div class="filter"><button class="chip" data-compare="open">📊 Comparar regiões</button></div>' + regions.map(([tag, r]) => {
    const color = LEVEL_COLOR[r.level] || LEVEL_COLOR.baixa;
    const warn = r.spike ? ` · <span class="alertline">⚠ ${r.spike_ratio?.toFixed?.(1) || ''}× o normal</span>` : '';
    const sig = (r.signals || []).length ? ' · <span class="alertline">📡 sinal do Radar</span>' : '';
    return `<button class="row region-row${S.region === tag ? ' sel' : ''}" data-region="${tag}">
      <span class="name">${esc(label(tag))}</span><span class="score count" style="color:${color}">${r.tension}</span>
      <span class="bar"><i style="width:${r.tension}%;background:${color}"></i></span>
      <span class="m">${esc(r.level)} · ${r.last24} histórias em 24 h${warn}${sig}</span></button>`;
  }).join('');
}

function storyRow(c) {
  const badges = [c.urgent ? '<span class="urg">URGENTE</span>' : '', c.sides === 'opostos' ? '🤝 LADOS OPOSTOS' : c.sides === 'um_lado' ? '⚠ SÓ UM LADO' : ''].filter(Boolean).join(' · ');
  const translated = c.lang === 'pt' || TR.get(c.title, c.lang);
  return `<button class="row${S.story === c.id ? ' sel' : ''}" data-story="${c.id}">
    ${badges ? `<div class="badges">${badges}</div>` : ''}
    <div class="t">${translated ? '' : `<span class="lang">${c.lang.toUpperCase()}</span>`}${esc(title(c))}</div>
    <div class="m">${esc(c.source)}${c.sources_count > 1 ? ` · ${c.sources_count} veículos` : ''} · ${rel(c.updated)} · ${c.tags.map((t) => esc(label(t))).slice(0, 2).join(' · ')}</div></button>`;
}

function newsList() {
  let list = S.feed.clusters;
  let head = '';
  if (S.region) {
    list = list.filter((c) => c.tags.includes(S.region));
    head = `<div class="filter"><span class="chip" data-clear-region>${esc(label(S.region))} ✕</span></div>`;
  }
  list = [...list].sort((a, b) => (b.urgent - a.urgent) || (b.score - a.score)).slice(0, 80);
  return head + (list.length ? list.map(storyRow).join('') : '<p class="muted" style="padding:14px">Nada nesta região agora.</p>');
}

function radarList(alerts) {
  const r = S.radar;
  if (!r) return '<p class="muted" style="padding:14px">Radar indisponível.</p>';
  const parts = [];
  parts.push(`<div class="card${alerts.length ? ' alert' : ''}"><h4>👁 ${alerts.length ? 'Sinais de alerta agora' : 'Nenhum sinal de alerta agora'}<small>${rel(r.generated_at)}</small></h4>
    ${alerts.map((a) => `<p class="alertline" data-region="${a.tag || ''}" data-layer-on="${a.layer || ''}" style="cursor:pointer">${esc(a.text)}</p>`).join('') ||
      '<p class="small muted">Internet, espaço aéreo, focos de calor, navios e aviões militares dentro do normal.</p>'}</div>`);
  const mil = r.military;
  if (mil) {
    const total = (mil.zones || []).reduce((s, z) => s + (z.count || 0), 0);
    parts.push(`<div class="card"><h4>✈ Aviões militares no ar<small>adsb.lol · ${rel(mil.updated)}</small></h4>
      <p>${total} com transponder ligado · ${(mil.zones || []).map((z) => `${esc(z.name)} ${z.count}`).join(' · ')}</p>
      <button class="chip" data-layer-on="aircraft">Mostrar no globo</button></div>`);
  }
  const car = r.carriers;
  if (car) {
    const near = (car.ships || []).filter((s) => s.lat >= 10 && s.lat <= 40 && s.lon >= 25 && s.lon <= 65).length;
    parts.push(`<div class="card"><h4>⚓ Porta-aviões dos EUA<small>USNI News · ${rel(car.updated)}</small></h4>
      <p>${near} no Oriente Médio e arredores · ${(car.ships || []).length} no total</p>
      <button class="chip" data-layer-on="carriers">Mostrar no globo</button></div>`);
  }
  const fl = r.frontline;
  if (fl) {
    const d = fl.change_7d_km2;
    const change = d == null ? '' : d > 0 ? `<span class="alertline">▲ Rússia avançou ${fmt(d)} km² em 7 dias</span>` : d < 0 ? `▼ Ucrânia retomou ${fmt(-d)} km² em 7 dias` : 'sem mudança em 7 dias';
    parts.push(`<div class="card"><h4>🗺 Linha de frente na Ucrânia<small>DeepStateMap · ${rel(fl.updated)}</small></h4>
      <p>Ocupado pela Rússia: <b><span class="count">${fmt(fl.occupied_km2)}</span> km²</b><br>${change}</p>
      <button class="chip" data-layer-on="front" data-fly="36.5,47.8,5.2">Mostrar no globo</button></div>`);
  }
  parts.push(marketsCards());
  const cw = r.crisiswatch;
  if (cw) {
    const g = (lab, list) => (list || []).length ? `<p class="small"><b>${lab}:</b> ${list.map((c) => esc(label(c.tag) !== c.tag && c.tag ? label(c.tag) : c.name)).join(', ')}</p>` : '';
    parts.push(`<div class="card${(cw.deteriorated || []).length ? ' alert' : ''}"><h4>📉 CrisisWatch ${esc(cw.month || '')}<small>Crisis Group</small></h4>
      ${g('▼ Pioraram', cw.deteriorated)}${g('▲ Melhoraram', cw.improved)}${g('⚠ Risco de conflito', cw.risk)}${g('🕊 Chance de solução', cw.resolution)}</div>`);
  }
  return parts.join('');
}

// ---------------------------------------------------------------------------
// Painel da direita: região, notícia, cidade
// ---------------------------------------------------------------------------
function showDetail(html) {
  $('#detail-body').innerHTML = html;
  $('#detail').hidden = false;
  $('#detail-body').scrollTop = 0;
}
function closeDetail() {
  $('#detail').hidden = true;
  S.region = null; S.story = null;
  history.replaceState(null, '', location.pathname);
  renderAll();
}

function tensionChart(tag) {
  const days = (S.stats?.days || []).slice(-30).map((d) => [d.date, d.tension?.[tag]]).filter(([, v]) => v != null);
  if (days.length < 2) return '<p class="small muted">O histórico de tensão enche com os dias.</p>';
  const w = 380, h = 70, slot = w / days.length, bar = slot * 0.7;
  const bars = days.map(([date, v], i) => {
    const bh = Math.max(2, (h - 12) * v / 100);
    return `<rect x="${(i * slot + (slot - bar) / 2).toFixed(1)}" y="${(h - bh).toFixed(1)}" width="${bar.toFixed(1)}" height="${bh.toFixed(1)}" fill="${LEVEL_COLOR[levelOf(v)]}"><title>${date}: ${v}</title></rect>`;
  }).join('');
  const y75 = h - (h - 12) * 0.75;
  return `<svg class="chart" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">${bars}<line x1="0" x2="${w}" y1="${y75}" y2="${y75}" stroke="#B3122E" stroke-opacity=".5" stroke-dasharray="3 3"/></svg>
    <p class="small muted">Tensão nos últimos ${days.length} dias · linha vermelha = crítica (75)</p>`;
}

function flagsHtml(tag) {
  const files = S.geo?.flags?.[tag] || [];
  if (!files.length) return '';
  return `<div class="flags">${files.map((f) => `<img alt="" loading="lazy" onerror="this.remove()" src="https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(f)}?width=80">`).join('')}</div>`;
}

function openRegion(tag, fly) {
  S.region = tag; S.story = null;
  history.replaceState(null, '', `#regiao=${tag}`);
  const r = S.feed?.regions?.[tag] || { tension: 0, level: 'baixa', last24: 0, baseline: 0 };
  const color = LEVEL_COLOR[r.level];
  const conflict = S.geo?.conflicts?.[tag];
  const day = conflict ? Math.floor((Date.now() - Date.parse(conflict.start)) / 86400000) + 1 : null;
  const stories = S.feed.clusters.filter((c) => c.tags.includes(tag)).sort((a, b) => b.score - a.score);
  const lines = regionRadarLines(tag);
  const signals = (r.signals || []).map((s) => `<p class="alertline">📡 ${esc(s.name || s.kind)}: ${esc(s.status)}</p>`).join('');
  const ms = S.geo?.milestones?.[tag] || [];
  showDetail(`
    <div class="detail-head">
      ${flagsHtml(tag)}
      <div class="kicker">Região</div>
      <h2>${esc(label(tag))}</h2>
      ${day ? `<div><span class="day">DIA ${fmt(day)}</span> <span class="muted small">${esc(conflict.label)} (desde ${new Date(conflict.start).toLocaleDateString('pt-BR')})</span></div>` : ''}
    </div>
    <div class="card${r.spike || r.level === 'crítica' ? ' alert' : ''}"><h4>Tensão<small>${esc(r.level)}</small></h4>
      <div class="count" style="font:700 28px var(--mono);color:${color}">${r.tension}</div>
      <div class="gauge"><i style="width:${r.tension}%;background:${color}"></i></div>
      <p class="small muted">${r.last24} histórias nas últimas 24 h · média ${Math.round(r.baseline || 0)} por dia</p>
      ${r.spike ? `<p class="alertline">⚠ Alta incomum: ritmo ${(r.spike_ratio || 0).toFixed(1)}× o normal nas últimas 6 h</p>` : ''}
      ${signals}
      ${tensionChart(tag)}
    </div>
    ${lines.length ? `<div class="card${lines.some((l) => l[1]) ? ' alert' : ''}"><h4>📡 Radar<small>${rel(S.radar?.generated_at)}</small></h4>
      ${lines.map(([t, a]) => `<p class="small${a ? ' alertline' : ''}">${esc(t)}</p>`).join('')}</div>` : ''}
    ${S.geo?.context?.[tag] ? `<div class="card"><h4>Contexto<small>até 2025</small></h4><p class="small">${esc(S.geo.context[tag])}</p>
      ${ms.length ? `<details><summary>Marcos (${ms.length})</summary>${ms.map((m) => `<p class="small"><b>${esc(m.date)}</b> · ${esc(m.text)}</p>`).join('')}</details>` : ''}</div>` : ''}
    <div class="card"><h4>Agora · últimas 48 h<small>${stories.length} histórias</small></h4></div>
    ${stories.slice(0, 40).map(storyRow).join('') || '<p class="muted" style="padding:0 16px">Nenhuma história desta região no momento.</p>'}
  `);
  if (fly) {
    const p = S.geo?.points?.[tag];
    if (p) flyCam({ center: p, zoom: Math.max(map.getZoom(), tag === 'eua' || tag === 'asia' || tag === 'africa' ? 3 : 4.6), speed: 0.9, curve: 1.4 });
  }
  renderAll();
}

function storyPlace(c) {
  for (const [name, list] of S.cityHits) if (list.some((x) => x.id === c.id)) return (S.geo.cities.find((ct) => ct.name === name) || null);
  return null;
}

function openStory(id, fly) {
  const c = findCluster(id);
  if (!c) return;
  S.story = id;
  history.replaceState(null, '', `#noticia=${id}`);
  const origins = S.geo?.origins || {};
  const groups = {};
  for (const a of c.articles) (groups[a.origin || 'internacional'] ||= []).push(a);
  const perspectives = Object.keys(groups).length >= 2 ? `<div class="card"><h4>Como cada lado noticiou</h4>${Object.entries(groups).map(([o, arts]) =>
    `<p class="small" style="color:var(--gold);margin-top:8px">${esc((origins[o] || o).toUpperCase())}</p>${arts.map((a) =>
      `<a class="art" href="${esc(a.url)}" target="_blank" rel="noopener"><div class="t">${esc(TR.show(a.title, a.lang))}</div><div class="m">${esc(a.source)}</div></a>`).join('')}`).join('')}</div>` : '';
  const figures = Object.entries(c.figures || {}).map(([k, info]) => `<p class="small"><b>${k === 'killed' ? 'Mortos' : k === 'injured' ? 'Feridos' : esc(k)}:</b> ${Object.entries(info.by_source || {}).sort((a, b) => b[1] - a[1]).map(([s, n]) => `${fmt(n)} (${esc(s)})`).join(' · ')}</p>`).join('');
  const divergent = Object.values(c.figures || {}).some((f) => f.divergent);
  const ordered = [...c.articles].sort((a, b) => a.published.localeCompare(b.published));
  const sides = c.sides === 'opostos'
    ? '<div class="card"><h4>🤝 Confirmado por lados opostos</h4><p class="small">Veículos de lados rivais contam esta história. Fatos que os dois lados relatam costumam ser mais sólidos.</p></div>'
    : c.sides === 'um_lado' ? '<div class="card alert"><h4>⚠ Só um lado noticiou</h4><p class="small">Até agora só uma origem publicou esta história. Vale esperar confirmação.</p></div>' : '';
  const edits = c.articles.filter((a) => (a.edits || []).length);
  const place = storyPlace(c);
  const translatedUrl = `https://translate.google.com/translate?sl=auto&tl=pt&u=${encodeURIComponent(c.url)}`;
  const summary = c.summary ? TR.show(c.summary, c.lang) : '';
  showDetail(`
    ${c.image ? `<img class="hero" alt="" loading="lazy" referrerpolicy="no-referrer" src="${esc(c.image.replace(/&amp;/g, '&'))}" onerror="this.remove()">` : ''}
    <div class="detail-head">
      <div class="kicker">${c.urgent ? '<span style="color:var(--blood)">URGENTE · </span>' : ''}Notícia</div>
      <h2>${esc(title(c))}</h2>
      <div class="m">${esc(c.source)} · ${dayClock(c.updated)} · ${c.sources_count} ${c.sources_count === 1 ? 'veículo' : 'veículos'}${c.lang !== 'pt' ? ` · original em ${LANG_NAMES[c.lang] || c.lang}` : ''}</div>
      <div style="margin-top:8px">${c.tags.map((t) => `<span class="chip" data-region="${t}">${esc(label(t))}</span>`).join('')}</div>
    </div>
    ${summary ? `<div class="summary">${esc(summary)}</div>` : ''}
    <div class="linkrow">
      <a class="btn gold" href="${esc(c.url)}" target="_blank" rel="noopener">Abrir no site</a>
      ${c.lang !== 'pt' ? `<a class="btn" href="${esc(translatedUrl)}" target="_blank" rel="noopener">Ler traduzido</a>` : ''}
      ${place ? `<button class="btn" data-fly="${place.lon},${place.lat},7">📍 ${esc(place.name)}</button>` : ''}
    </div>
    ${sides}
    ${figures ? `<div class="card${divergent ? ' alert' : ''}"><h4>${divergent ? '⚠ Números divergentes' : '🔢 Números citados'}</h4>${figures}</div>` : ''}
    ${edits.length ? `<div class="card"><h4>✏ Manchete alterada</h4>${edits.map((a) => `<p class="small"><b>${esc(a.source)}</b><br><s class="muted">${esc(a.edits[0].title)}</s><br>${esc(a.title)}</p>`).join('')}</div>` : ''}
    ${(c.framing || []).length ? `<div class="card"><h4>🗣 Palavras de cada lado</h4>${c.framing.map((g) => `<p class="small"><b>${esc(g.group)}</b><br>${Object.entries(g.by_origin || {}).map(([o, terms]) => `${esc(origins[o] || o)}: ${terms.map((t) => `“${esc(t)}”`).join(', ')}`).join('<br>')}</p>`).join('')}</div>` : ''}
    ${perspectives}
    <div class="card"><h4>Linha do tempo<small>${c.sources_count} veículos</small></h4>
      <div class="tl">${ordered.map((a, i) => `<a class="art${i === 0 ? ' first' : ''}" href="${esc(a.url)}" target="_blank" rel="noopener">
        <div class="m">${dayClock(a.published)} · ${esc(a.source)}${i === 0 ? ' · primeiro a noticiar' : ''}</div><div class="t small">${esc(TR.show(a.title, a.lang))}</div></a>`).join('')}</div></div>
  `);
  if (fly) {
    // Região mais específica da notícia (EUA, OTAN, Ásia e África só quando não há outra).
    const broad = ['eua', 'otan', 'asia', 'africa', 'ice', 'brasil', 'mediterraneo'];
    const tag = c.tags.find((t) => !broad.includes(t)) || c.tags[0];
    const target = place ? [place.lon, place.lat] : S.geo?.points?.[tag];
    if (target) flyCam({ center: target, zoom: place ? 6.5 : 4.5, speed: 0.9 });
  }
  renderAll();
}

function openCity(name) {
  const city = S.geo.cities.find((c) => c.name === name);
  const list = S.cityHits.get(name) || [];
  S.story = null;
  showDetail(`<div class="detail-head"><div class="kicker">Cidade · ${esc(label(city?.tag))}</div><h2>${esc(name)}</h2>
    <div class="m">${list.length} ${list.length === 1 ? 'notícia cita' : 'notícias citam'} esta cidade agora</div></div>
    ${list.map(storyRow).join('')}`);
  if (city) flyCam({ center: [city.lon, city.lat], zoom: Math.max(map.getZoom(), 6.5), speed: 0.9 });
}

// ---------------------------------------------------------------------------
// Barra de cima, faixa de baixo, busca
// ---------------------------------------------------------------------------
function renderTop() {
  const g = S.feed?.global;
  if (g) {
    $('#clock-n').textContent = g.index;
    $('#clock-n').style.color = LEVEL_COLOR[g.level];
    $('#clock-level').textContent = `Relógio do Argos · ${g.level}`;
    $('#clock-sub').textContent = `tensão global · puxado por ${label(g.leader)}`;
    $('#clock').classList.toggle('critica', g.level === 'crítica');
  }
  if (S.feed) $('#updated').textContent = `Atualizado ${rel(S.feed.generated_at)}`;
}

function renderTicker() {
  const now = Date.now();
  const items = (S.feed?.clusters || []).filter((c) => now - Date.parse(c.updated) < 6 * 3600000)
    .sort((a, b) => (b.urgent - a.urgent) || b.updated.localeCompare(a.updated)).slice(0, 10);
  $('#ticker').hidden = !items.length;
  $('#marquee').innerHTML = items.map((c) => `<button data-story="${c.id}">${c.urgent ? '<span class="u">URGENTE · </span>' : ''}${esc(title(c))}</button>`).join('<span class="muted">◆</span>');
}

function renderAll() {
  renderTop();
  renderLeft();
  renderTicker();
  renderRegionMarkers();
}

function search(q) {
  const box = $('#results');
  const terms = norm(q).split(/\s+/).filter(Boolean);
  if (!terms.length || !S.feed) { box.hidden = true; return; }
  const has = (text) => terms.every((t) => norm(text).includes(t));
  const regions = Object.entries(S.geo?.labels || {}).filter(([, l]) => has(l)).slice(0, 5);
  const cities = (S.geo?.cities || []).filter((c) => has(c.name) || c.terms.some((t) => has(t))).slice(0, 5);
  const stories = S.feed.clusters.filter((c) => has([c.title, TR.get(c.title, c.lang), c.summary, TR.get(c.summary, c.lang), ...c.articles.map((a) => a.title)].join(' '))).slice(0, 12);
  const parts = [];
  if (regions.length) parts.push('<div class="grp">Regiões</div>' + regions.map(([tag, l]) => `<button data-region="${tag}">${esc(l)}</button>`).join(''));
  if (cities.length) parts.push('<div class="grp">Cidades</div>' + cities.map((c) => `<button data-city="${esc(c.name)}">${esc(c.name)} <small>${esc(label(c.tag))} · ${S.cityHits.get(c.name)?.length || 0} notícias</small></button>`).join(''));
  if (stories.length) parts.push('<div class="grp">Notícias</div>' + stories.map((c) => `<button data-story="${c.id}">${esc(title(c))} <small>${esc(c.source)} · ${rel(c.updated)}</small></button>`).join(''));
  box.innerHTML = parts.join('') || '<div class="grp">Nada encontrado</div>';
  box.hidden = false;
}

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.hidden = true; }, 4000);
}

function setLayer(id, on) {
  S.layers[id] = on;
  store.set('argos_layers', S.layers);
  const box = document.querySelector(`[data-layer="${id}"]`);
  if (box) box.checked = on;
  renderLayers();
}

// Um só ouvinte de cliques para tudo que tem data-*.
document.addEventListener('click', (e) => {
  const t = e.target.closest('[data-region],[data-story],[data-city],[data-layer-on],[data-fly],[data-clear-region],[data-tab],[data-compare],[data-action]');
  if (!t) {
    if (!e.target.closest('.search')) $('#results').hidden = true;
    if (!e.target.closest('#layers') && !e.target.closest('#btn-layers')) { $('#layers').hidden = true; $('#btn-layers').setAttribute('aria-expanded', 'false'); }
    return;
  }
  $('#results').hidden = true;
  const d = t.dataset;
  if (d.tab) { switchTab(d.tab); return; }
  if (d.compare) { if (d.compare === 'open') openCompare(); else toggleCompare(d.compare); return; }
  if (d.action === 'share') { shareImage(); return; }
  if (d.action === 'help') { openHelp(); return; }
  if (d.layerOn) setLayer(d.layerOn, true);
  if (d.fly) { const [lon, lat, z] = d.fly.split(',').map(Number); flyCam({ center: [lon, lat], zoom: z, speed: 0.9 }); }
  if (d.clearRegion !== undefined) { S.region = null; renderAll(); return; }
  if (d.story) openStory(d.story, true);
  else if (d.city) openCity(d.city);
  else if (d.region) openRegion(d.region, true);
});

$('#detail-close').onclick = closeDetail;
$('#clock').onclick = () => { if (S.feed?.global?.leader) openRegion(S.feed.global.leader, true); };
$('#btn-layers').onclick = () => {
  const p = $('#layers');
  p.hidden = !p.hidden;
  $('#btn-layers').setAttribute('aria-expanded', String(!p.hidden));
};
document.querySelectorAll('[data-layer]').forEach((box) => {
  box.checked = !!S.layers[box.dataset.layer];
  box.onchange = () => setLayer(box.dataset.layer, box.checked);
});
document.querySelectorAll('input[name="base"]').forEach((r) => {
  r.checked = r.value === S.base;
  r.onchange = () => setBase(r.value);
});
const rot = document.querySelector('[data-opt="rotate"]');
rot.checked = S.rotate;
rot.onchange = () => { S.rotate = rot.checked; store.set('argos_rotate', S.rotate); };

const q = $('#q');
q.addEventListener('input', () => search(q.value));
q.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { const first = $('#results button'); if (first) first.click(); }
  if (e.key === 'Escape') { q.value = ''; $('#results').hidden = true; q.blur(); }
});
document.addEventListener('keydown', (e) => {
  if (e.key === '/' && document.activeElement !== q) { e.preventDefault(); q.focus(); }
  if (e.key === 'Escape' && document.activeElement !== q && !$('#detail').hidden) closeDetail();
});

// Coordenadas do cursor e hora UTC no canto.
map.on('mousemove', (e) => {
  const { lat, lng } = e.lngLat;
  $('#hud-pos').textContent = `${Math.abs(lat).toFixed(2)}° ${lat >= 0 ? 'N' : 'S'}  ${Math.abs(lng).toFixed(2)}° ${lng >= 0 ? 'L' : 'O'}  ·  zoom ${map.getZoom().toFixed(1)}`;
});
setInterval(() => { $('#hud-utc').textContent = `UTC ${new Date().toISOString().slice(11, 16)}`; }, 1000);

// ---------------------------------------------------------------------------
// Início
// ---------------------------------------------------------------------------
function openFromHash() {
  const m = location.hash.match(/^#(regiao|noticia)=(.+)$/);
  if (!m) return;
  if (m[1] === 'regiao') openRegion(decodeURIComponent(m[2]), true);
  else openStory(decodeURIComponent(m[2]), true);
}

const loaded = load();
map.on('load', async () => {
  map.setProjection({ type: 'globe' });
  setBase(S.base);
  addDataLayers();
  await loaded;
  renderAll();
  renderLayers();
  openFromHash();
  requestAnimationFrame(spin);
  if (S.feed) setupTranslation();
});
loaded.then(() => { renderAll(); });

// Dados novos a cada 5 minutos (o servidor atualiza a cada ~30 min).
setInterval(async () => {
  await load();
  renderAll();
  renderLayers();
  if (S.story) openStory(S.story, false);
  else if (S.region) openRegion(S.region, false);
  TR.run();
}, 5 * 60 * 1000);
