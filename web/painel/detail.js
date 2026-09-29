/* Argos · Painel — mais detalhe no mapa, tudo gratuito e sem chave:
 *  - relevo 3D e sombreamento (AWS Terrain Tiles, dados abertos da Mapzen)
 *  - prédios 3D, ruas e nomes nítidos (OpenFreeMap, com dados do OpenStreetMap)
 *  - nuvens quase ao vivo (Meteosat/EUMETSAT, infravermelho, a cada ~15 min)
 *  - luzes das cidades só do lado da noite (GeoNames, cidades com 150 mil+ habitantes)
 *  - foto de satélite do dia (NASA GIBS, VIIRS) como opção de fundo
 * Cidades em foto 3D realista (Google/Cesium) exigem conta e chave: ficam de fora. */
'use strict';

const STRONG_PC = (navigator.hardwareConcurrency || 4) >= 8 && (navigator.deviceMemory || 8) >= 8;
const DETAIL_DEFAULTS = { terrain: STRONG_PC, buildings: STRONG_PC, clouds: true, lights: true };
for (const [k, v] of Object.entries(DETAIL_DEFAULTS)) if (S.layers[k] === undefined) S.layers[k] = v;

const NASA_DAY = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
const cloudsUrl = () => 'https://view.eumetsat.int/geoserver/wms?service=WMS&version=1.3.0&request=GetMap' +
  '&layers=mumi:worldcloudmap_ir108&styles=&crs=EPSG:3857&bbox={bbox-epsg-3857}&width=256&height=256' +
  `&format=image/png&transparent=true&v=${Math.floor(Date.now() / 900000)}`;

function addDetailLayers() {
  if (map.getSource('dem')) return;
  map.setGlyphs('https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf');
  const dem = {
    type: 'raster-dem', encoding: 'terrarium', tileSize: 256, maxzoom: 14,
    tiles: ['https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'],
    attribution: 'Relevo: Mapzen Terrain Tiles (AWS)',
  };
  map.addSource('dem', dem);
  map.addSource('dem-shade', { ...dem });
  map.addSource('nasa', {
    type: 'raster', tileSize: 256, maxzoom: 9,
    tiles: [`https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/VIIRS_NOAA20_CorrectedReflectance_TrueColor/default/${NASA_DAY}/GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg`],
    attribution: 'Imagem do dia: NASA GIBS / VIIRS',
  });
  map.addSource('clouds', { type: 'raster', tileSize: 256, tiles: [cloudsUrl()], attribution: 'Nuvens: EUMETSAT' });
  map.addSource('omt', { type: 'vector', url: 'https://tiles.openfreemap.org/planet', attribution: '© OpenFreeMap © OpenStreetMap' });
  map.addSource('lights', { type: 'geojson', data: 'lights.geojson', attribution: 'Cidades: GeoNames' });

  // Fundo "NASA hoje" junto dos outros fundos; relevo sombreado por cima deles.
  map.addLayer({ id: 'nasa', type: 'raster', source: 'nasa', layout: { visibility: 'none' }, paint: { 'raster-brightness-max': 0.85 } }, 'labels');
  map.addLayer({
    id: 'hillshade', type: 'hillshade', source: 'dem-shade', layout: { visibility: 'none' },
    paint: { 'hillshade-exaggeration': 0.45, 'hillshade-shadow-color': '#000000', 'hillshade-highlight-color': '#b89a4a', 'hillshade-accent-color': '#2a2412' },
  }, 'labels');
  // Ruas, prédios e nomes (acima das fronteiras, abaixo da noite e dos dados).
  map.addLayer({
    id: 'omt-roads', type: 'line', source: 'omt', 'source-layer': 'transportation', minzoom: 9, layout: { visibility: 'none', 'line-cap': 'round' },
    filter: ['match', ['get', 'class'], ['motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor'], true, false],
    paint: {
      'line-color': ['match', ['get', 'class'], ['motorway', 'trunk'], '#7a6a3c', '#4a4434'],
      'line-opacity': ['interpolate', ['linear'], ['zoom'], 9, 0.35, 14, 0.85],
      'line-width': ['interpolate', ['exponential', 1.6], ['zoom'], 9, ['match', ['get', 'class'], ['motorway', 'trunk'], 1.2, 0.4], 16, ['match', ['get', 'class'], ['motorway', 'trunk'], 9, ['primary', 'secondary'], 6, 3]],
    },
  }, 'night');
  map.addLayer({
    id: 'omt-buildings', type: 'fill-extrusion', source: 'omt', 'source-layer': 'building', minzoom: 13, layout: { visibility: 'none' },
    paint: {
      'fill-extrusion-color': ['interpolate', ['linear'], ['coalesce', ['get', 'render_height'], 6], 0, '#1d1b16', 25, '#3b3527', 80, '#6b5c33', 200, '#c9a227'],
      'fill-extrusion-height': ['interpolate', ['linear'], ['zoom'], 13, 0, 14.5, ['coalesce', ['get', 'render_height'], 6]],
      'fill-extrusion-base': ['coalesce', ['get', 'render_min_height'], 0],
      'fill-extrusion-opacity': 0.9,
    },
  }, 'night');
  map.addLayer({
    id: 'omt-places', type: 'symbol', source: 'omt', 'source-layer': 'place', minzoom: 5,
    filter: ['any',
      ['match', ['get', 'class'], ['city'], true, false],
      ['all', ['match', ['get', 'class'], ['town'], true, false], ['>=', ['zoom'], 7]],
      ['all', ['match', ['get', 'class'], ['village', 'suburb'], true, false], ['>=', ['zoom'], 11]]],
    layout: {
      visibility: 'none',
      'text-field': ['coalesce', ['get', 'name:pt'], ['get', 'name:latin'], ['get', 'name']],
      'text-font': ['Noto Sans Regular'],
      'text-size': ['interpolate', ['linear'], ['zoom'], 5, ['match', ['get', 'class'], 'city', 12, 10], 12, ['match', ['get', 'class'], 'city', 17, 13]],
      'text-letter-spacing': 0.03,
      'text-max-width': 8,
    },
    paint: { 'text-color': '#E8E2D0', 'text-halo-color': '#050505', 'text-halo-width': 1.4, 'text-opacity': 0.9 },
  }, 'night');
  // Nuvens por cima do mapa, embaixo da noite e dos marcadores.
  map.addLayer({
    id: 'clouds', type: 'raster', source: 'clouds', layout: { visibility: 'none' },
    paint: { 'raster-opacity': 0.5, 'raster-contrast': 0.35, 'raster-saturation': -1, 'raster-fade-duration': 600 },
  }, 'night');
  // Luzes das cidades: só as que estão do lado escuro (filtro "within" com o polígono da noite).
  map.addLayer({
    id: 'lights-glow', type: 'circle', source: 'lights', layout: { visibility: 'none' },
    paint: {
      'circle-color': '#FFB347', 'circle-blur': 1, 'circle-opacity': 0.35,
      'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, ['min', 7, ['+', 1.5, ['*', 0.06, ['sqrt', ['get', 'p']]]]], 6, ['min', 30, ['+', 5, ['*', 0.25, ['sqrt', ['get', 'p']]]]]],
    },
  });
  map.addLayer({
    id: 'lights', type: 'circle', source: 'lights', layout: { visibility: 'none' },
    paint: {
      'circle-color': '#FFE3A3', 'circle-blur': 0.6, 'circle-opacity': 0.9,
      'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, ['min', 2.5, ['+', 0.5, ['*', 0.02, ['sqrt', ['get', 'p']]]]], 6, ['min', 9, ['+', 1.5, ['*', 0.07, ['sqrt', ['get', 'p']]]]]],
    },
  });
  applyDetail();
}

/** Relevo 3D só com o globo perto (longe, só gasta processamento e não aparece). */
function updateTerrain() {
  const want = S.layers.terrain && map.getZoom() >= 4.5;
  const has = !!map.getTerrain();
  if (want && !has) map.setTerrain({ source: 'dem', exaggeration: 1.6 });
  else if (!want && has) map.setTerrain(null);
}

function updateLights() {
  if (!map.getLayer('lights')) return;
  const on = S.layers.lights && S.layers.night;
  for (const id of ['lights', 'lights-glow']) {
    map.setLayoutProperty(id, 'visibility', on ? 'visible' : 'none');
    if (on) map.setFilter(id, ['within', nightPolygon().geometry]);
  }
}

function applyDetail() {
  if (!map.getLayer('omt-buildings')) return;
  const vis = (on) => (on ? 'visible' : 'none');
  const L = S.layers;
  map.setLayoutProperty('hillshade', 'visibility', vis(L.terrain));
  for (const id of ['omt-roads', 'omt-buildings', 'omt-places']) map.setLayoutProperty(id, 'visibility', vis(L.buildings));
  map.setLayoutProperty('clouds', 'visibility', vis(L.clouds));
  map.setPaintProperty('clouds', 'raster-opacity', S.base === 'sat' || S.base === 'nasa' ? 0.3 : 0.5);
  // Nomes vetoriais (nítidos) substituem os nomes em imagem da Esri.
  if (map.getLayer('labels')) map.setLayoutProperty('labels', 'visibility', vis(!L.buildings && S.base !== 'none'));
  map.setLayoutProperty('nasa', 'visibility', vis(S.base === 'nasa'));
  updateTerrain();
  updateLights();
  const hq = $('[data-opt="hq"]');
  if (hq) hq.checked = L.terrain && L.buildings;
}

// Os fundos, as camadas e a noite de app.js/features.js passam a cuidar também das novas.
const _setBase = setBase;
setBase = function (base) {
  _setBase(base === 'nasa' ? 'none' : base);
  S.base = base;
  store.set('argos_base', base);
  applyDetail();
};
const _setLayer = setLayer;
setLayer = function (id, on) {
  _setLayer(id, on);
  applyDetail();
};
const _renderNight = renderNight;
renderNight = function () {
  _renderNight();
  updateLights();
};

map.on('zoomend', updateTerrain);
// A noite anda: as luzes acompanham a cada minuto.
setInterval(updateLights, 60000);
// Nuvens novas a cada 15 minutos.
setInterval(() => {
  const src = map.getSource('clouds');
  if (src && S.layers.clouds) src.setTiles([cloudsUrl()]);
}, 15 * 60 * 1000);

if (map.loaded() && map.getLayer('night')) addDetailLayers();
else map.on('load', () => setTimeout(addDetailLayers, 0));

// Caixas das camadas novas e do "Alta qualidade".
document.querySelectorAll('[data-layer="terrain"],[data-layer="buildings"],[data-layer="clouds"],[data-layer="lights"]').forEach((box) => {
  box.checked = !!S.layers[box.dataset.layer];
});
const hqBox = $('[data-opt="hq"]');
if (hqBox) {
  hqBox.checked = S.layers.terrain && S.layers.buildings;
  hqBox.onchange = () => {
    setLayer('terrain', hqBox.checked);
    setLayer('buildings', hqBox.checked);
  };
}
const nasaRadio = document.querySelector('input[name="base"][value="nasa"]');
if (nasaRadio) nasaRadio.checked = S.base === 'nasa';
