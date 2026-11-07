/* Argos · Painel — movimento: abertura, voo de câmera, varredura de radar, ping de notícia nova,
 * arcos que se desenham, contornos que acendem, números contando, painéis em cascata, fogo
 * tremulando, frente viva, alvorada/crepúsculo, retícula no cursor e urgente datilografado.
 * Tudo desliga com "reduzir movimento" do sistema. Carregado depois de app.js e features.js. */
'use strict';

const REDUCED = matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---------------------------------------------------------------------------
// 1. Abertura: o olho se desenha e o globo desce das estrelas até o Oriente Médio
// ---------------------------------------------------------------------------
(function intro() {
  const splash = $('#splash');
  const deep = /^#(regiao|noticia)=/.test(location.hash);
  if (REDUCED || deep) {
    splash?.remove();
    if (map.getZoom() < 2) map.jumpTo({ center: [40, 29], zoom: 2.2 });
    return;
  }
  setTimeout(() => splash?.classList.add('out'), 1700);
  setTimeout(() => splash?.remove(), 2400);
  const go = () => setTimeout(() => {
    map.flyTo({ center: [40, 29], zoom: 2.2, bearing: 0, duration: 4200, curve: 1.2, essential: true });
  }, 900);
  if (map.loaded()) go(); else map.once('load', go);
})();

// ---------------------------------------------------------------------------
// 2. Voo de câmera: sobe, gira e desce inclinado quando chega perto
// ---------------------------------------------------------------------------
function flyCam(opts) {
  const zoom = opts.zoom ?? map.getZoom();
  map.flyTo({
    speed: 0.7,
    curve: 1.7,
    pitch: zoom >= 5.5 ? 50 : zoom >= 4.2 ? 25 : 0,
    bearing: zoom >= 5.5 ? -12 : 0,
    ...opts,
  });
}

// ---------------------------------------------------------------------------
// 3. Varredura de radar sobre a região aberta
// ---------------------------------------------------------------------------
let sweep = null;
function setSweep(lnglat) {
  sweep?.remove();
  sweep = null;
  if (!lnglat || REDUCED) return;
  const el = document.createElement('div');
  el.className = 'sweep';
  el.innerHTML = '<i></i><b></b>';
  sweep = new maplibregl.Marker({ element: el, anchor: 'center' }).setLngLat(lnglat).addTo(map);
}

// ---------------------------------------------------------------------------
// 4. Ping quando chega notícia nova (e brilho dourado na lista)
// ---------------------------------------------------------------------------
S.fresh = new Set();
function ping(lnglat, urgent) {
  if (REDUCED || !lnglat || !visibleOnGlobe(lnglat[0], lnglat[1], 84)) return;
  const el = document.createElement('div');
  el.className = `ping${urgent ? ' urgent' : ''}`;
  el.innerHTML = '<i></i><i></i><i></i>';
  const m = new maplibregl.Marker({ element: el, anchor: 'center' }).setLngLat(lnglat).addTo(map);
  setTimeout(() => m.remove(), 4200);
}
function storyLngLat(c) {
  const place = storyPlace(c);
  if (place) return [place.lon, place.lat];
  const tag = c.tags.find((t) => !BROAD.includes(t)) || c.tags[0];
  return S.geo?.points?.[tag];
}

const _load = load;
load = async function () {
  const before = new Set((S.feed?.clusters || []).map((c) => c.id));
  await _load();
  if (!before.size || !S.feed) return;
  const fresh = S.feed.clusters.filter((c) => !before.has(c.id));
  S.fresh = new Set(fresh.map((c) => c.id));
  fresh.slice(0, 12).forEach((c, i) => setTimeout(() => ping(storyLngLat(c), c.urgent), i * 350));
  if (fresh.some((c) => c.urgent)) S.urgentNew = fresh.find((c) => c.urgent).id;
  setTimeout(() => { S.fresh.clear(); S.urgentNew = null; }, 60000);
};

const _storyRow = storyRow;
storyRow = function (c) {
  const html = _storyRow(c);
  return S.fresh.has(c.id) ? html.replace('class="row', 'class="row fresh') : html;
};

// ---------------------------------------------------------------------------
// 5. Arcos se desenham de uma região à outra antes de correr
// ---------------------------------------------------------------------------
let arcsKey = '';
const _renderArcs = renderArcs;
renderArcs = function () {
  _renderArcs();
  const src = map.getSource('arcs');
  if (!src || REDUCED || !S.layers.arcs) return;
  const feats = S.arcFeatures || [];
  const key = feats.map((f) => `${f.properties.a}|${f.properties.b}`).join(',');
  if (!feats.length || key === arcsKey) return;
  arcsKey = key;
  growLines(src, feats, 1400);
};
function growLines(src, feats, ms) {
  const t0 = performance.now();
  const full = feats.map((f) => f.geometry.coordinates);
  const step = (now) => {
    const t = Math.min(1, (now - t0) / ms);
    const e = 1 - Math.pow(1 - t, 3);
    src.setData(fc(feats.map((f, i) => ({ ...f, geometry: { type: 'LineString', coordinates: full[i].slice(0, Math.max(2, Math.ceil(full[i].length * e))) } }))));
    if (t < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

const _openPair = openPair;
openPair = function (a, b) {
  _openPair(a, b);
  const src = map.getSource('arcs');
  const all = S.arcFeatures || [];
  const feats = all.filter((f) => f.properties.a === a && f.properties.b === b);
  if (feats.length && !REDUCED) {
    arcsKey = '';
    const others = all.filter((f) => !(f.properties.a === a && f.properties.b === b));
    const t0 = performance.now();
    const full = feats[0].geometry.coordinates;
    const step = (now) => {
      const t = Math.min(1, (now - t0) / 1100);
      src.setData(fc([...others, { ...feats[0], geometry: { type: 'LineString', coordinates: full.slice(0, Math.max(2, Math.ceil(full.length * t))) } }]));
      if (t < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
    const pts = S.geo.points;
    const mid = [(pts[a][0] + pts[b][0]) / 2, (pts[a][1] + pts[b][1]) / 2];
    flyCam({ center: mid, zoom: Math.min(map.getZoom(), 3.4) });
  }
};

// ---------------------------------------------------------------------------
// 6. Contorno do país acende sob o mouse; região aberta com brilho
// ---------------------------------------------------------------------------
function addHoverLayer() {
  if (map.getLayer('land-hover')) return;
  map.addLayer({
    id: 'land-hover', type: 'line', source: 'countries', filter: ['==', ['get', 'name'], ''],
    paint: { 'line-color': '#E8C766', 'line-width': 2.2, 'line-opacity': 0.9, 'line-blur': 0.6 },
  }, 'night');
  map.addLayer({
    id: 'land-hover-glow', type: 'line', source: 'countries', filter: ['==', ['get', 'name'], ''],
    paint: { 'line-color': '#C9A227', 'line-width': 10, 'line-opacity': 0.18, 'line-blur': 8 },
  }, 'land-hover');
  let last = '';
  map.on('mousemove', (e) => {
    const f = map.queryRenderedFeatures(e.point, { layers: ['land'] })[0];
    const name = f?.properties?.name || '';
    if (name === last) return;
    last = name;
    for (const id of ['land-hover', 'land-hover-glow']) map.setFilter(id, ['==', ['get', 'name'], name]);
  });
  map.getCanvas().addEventListener('mouseleave', () => {
    last = '';
    for (const id of ['land-hover', 'land-hover-glow']) map.setFilter(id, ['==', ['get', 'name'], '']);
  });
}

// ---------------------------------------------------------------------------
// 7. Números contando e gráficos crescendo
// ---------------------------------------------------------------------------
function countUp(el, to, ms = 900) {
  if (REDUCED || !el) return;
  const fmtN = (n) => Math.round(n).toLocaleString('pt-BR');
  const suffix = el.dataset.suffix || '';
  const t0 = performance.now();
  const step = (now) => {
    const t = Math.min(1, (now - t0) / ms);
    el.textContent = fmtN(to * (1 - Math.pow(1 - t, 3))) + suffix;
    if (t < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}
function animateNumbers(root) {
  root.querySelectorAll('.count').forEach((el) => {
    const n = Number(el.textContent.replace(/\./g, '').replace(/[^\d-]/g, ''));
    if (Number.isFinite(n) && n > 0) countUp(el, n);
  });
}
function animateCharts(root) {
  if (REDUCED) return;
  root.querySelectorAll('svg.chart rect').forEach((r, i) => { r.style.animationDelay = `${Math.min(i, 40) * 22}ms`; r.classList.add('rise'); });
  root.querySelectorAll('svg.chart path').forEach((p) => {
    p.setAttribute('pathLength', '1');
    p.classList.add('draw');
  });
}

let lastClock = null;
const _renderTop = renderTop;
renderTop = function () {
  _renderTop();
  const g = S.feed?.global;
  if (g && g.index !== lastClock) {
    const el = $('#clock-n');
    if (lastClock == null || !REDUCED) countUp(el, g.index, lastClock == null ? 1400 : 700);
    lastClock = g.index;
  }
};

// ---------------------------------------------------------------------------
// 8. Painel da direita desliza; listas entram em cascata ao trocar de aba
// ---------------------------------------------------------------------------
const _showDetail = showDetail;
showDetail = function (html) {
  const panel = $('#detail');
  const wasHidden = panel.hidden;
  _showDetail(html);
  const body = $('#detail-body');
  if (!REDUCED) {
    panel.classList.remove('enter', 'swap');
    void panel.offsetWidth;
    panel.classList.add(wasHidden ? 'enter' : 'swap');
    cascade(body);
  }
  animateNumbers(body);
  animateCharts(body);
};
function cascade(root) {
  if (REDUCED) return;
  [...root.querySelectorAll('.row, .card')].slice(0, 24).forEach((el, i) => {
    el.style.animationDelay = `${i * 28}ms`;
    el.classList.add('in');
  });
}
const _switchTab = switchTab;
switchTab = function (tab) {
  _switchTab(tab);
  cascade($('#left-body'));
  animateNumbers($('#left-body'));
  animateCharts($('#left-body'));
};

const _openRegion = openRegion;
openRegion = function (tag, fly) {
  _openRegion(tag, fly);
  setSweep(S.geo?.points?.[tag]);
};
const _closeDetail = closeDetail;
closeDetail = function () {
  _closeDetail();
  setSweep(null);
  if (map.getPitch() > 0 && !REDUCED) map.easeTo({ pitch: 0, bearing: 0, duration: 900 });
};
const _openStory = openStory;
openStory = function (id, fly) {
  setSweep(null);
  _openStory(id, fly);
};

// ---------------------------------------------------------------------------
// 9 e 10. Fogo tremulando e frente viva (brilho pulsando na borda)
// ---------------------------------------------------------------------------
let phase = 0;
setInterval(() => {
  if (REDUCED || document.hidden) return;
  phase += 0.35;
  if (S.layers.fires && map.getLayer('fires')) {
    map.setPaintProperty('fires', 'circle-opacity', ['+', 0.55, ['*', 0.4, ['sin', ['+', ['*', ['get', 'seed'], 6.283], phase]]]]);
  }
  if (S.layers.front && map.getLayer('front-glow')) {
    const advancing = (S.radar?.frontline?.change_7d_km2 || 0) > 0;
    map.setPaintProperty('front-glow', 'line-opacity', Math.max(0, (advancing ? 0.4 : 0.25) + 0.2 * Math.sin(phase / 2)));
  }
}, 120);

// ---------------------------------------------------------------------------
// 11. Alvorada e crepúsculo: borda da noite em degradê laranja
// ---------------------------------------------------------------------------
function addTwilight() {
  if (!map.getLayer('night-edge')) return;
  map.setPaintProperty('night-edge', 'line-color', '#C8662B');
  map.setPaintProperty('night-edge', 'line-width', 26);
  map.setPaintProperty('night-edge', 'line-blur', 22);
  map.setPaintProperty('night-edge', 'line-opacity', 0.28);
  if (!map.getLayer('night-edge-core')) {
    map.addLayer({ id: 'night-edge-core', type: 'line', source: 'night', paint: { 'line-color': '#E8A45C', 'line-width': 5, 'line-blur': 4, 'line-opacity': 0.3 } }, 'night-edge');
  }
  if (!map.getLayer('front-glow') && map.getSource('front')) {
    map.addLayer({ id: 'front-glow', type: 'line', source: 'front', filter: ['==', ['get', 'kind'], 'occupied'], paint: { 'line-color': '#FF3355', 'line-width': 6, 'line-blur': 5, 'line-opacity': 0.3 } }, 'front-line');
  }
}

// ---------------------------------------------------------------------------
// 12. Retícula no cursor com coordenadas
// ---------------------------------------------------------------------------
const reticle = document.createElement('div');
reticle.className = 'reticle';
reticle.hidden = true;
reticle.innerHTML = '<i class="h"></i><i class="v"></i><span></span>';
document.body.appendChild(reticle);
map.on('mousemove', (e) => {
  reticle.hidden = false;
  reticle.style.transform = `translate(${e.originalEvent.clientX}px, ${e.originalEvent.clientY}px)`;
  const { lat, lng } = e.lngLat;
  reticle.querySelector('span').textContent = `${lat.toFixed(2)}, ${lng.toFixed(2)}`;
});
map.getCanvas().addEventListener('mouseleave', () => { reticle.hidden = true; });

// ---------------------------------------------------------------------------
// 13. Urgente novo na faixa: máquina de escrever e "ao vivo" piscando em vermelho
// ---------------------------------------------------------------------------
const _renderTicker = renderTicker;
renderTicker = function () {
  _renderTicker();
  if (!S.urgentNew || REDUCED) return;
  const btn = document.querySelector(`#marquee [data-story="${S.urgentNew}"]`);
  const ticker = $('#ticker');
  ticker.classList.add('alarm');
  setTimeout(() => ticker.classList.remove('alarm'), 8000);
  if (btn) {
    const text = btn.textContent;
    btn.textContent = '';
    btn.classList.add('typing');
    let i = 0;
    const tick = setInterval(() => {
      btn.textContent = text.slice(0, ++i);
      if (i >= text.length) { clearInterval(tick); btn.classList.remove('typing'); }
    }, 28);
  }
};

// Camadas extras quando o mapa termina de carregar (depois das de app.js).
function motionLayers() {
  addHoverLayer();
  addTwilight();
}
if (map.loaded() && map.getLayer('night')) motionLayers();
else map.on('load', () => setTimeout(motionLayers, 0));
