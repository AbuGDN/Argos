package com.abugdn.wid.ui

import android.graphics.Color as AndroidColor
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

// Mapas da Esri de uso livre, sem chave (os mesmos do painel web, web/painel/app.js): fundo cinza-escuro,
// que combina com o tema do app, e nomes de lugares numa camada à parte, em inglês — o OpenStreetMap
// padrão era claro e escrevia cada país na língua local (القاهرة, תל אביב, Київ).

/** Mosaico da Esri no formato z/y/x. */
private open class EsriTiles(name: String, service: String, ext: String, maxZoom: Int) : OnlineTileSourceBase(
    name, 0, maxZoom, 256, ext,
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/$service/MapServer/tile/"),
    "Esri",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

private object EsriDarkBase : EsriTiles("EsriDarkGrayBase", "Canvas/World_Dark_Gray_Base", ".jpg", 16)
private object EsriDarkLabels : EsriTiles("EsriDarkGrayReference", "Canvas/World_Dark_Gray_Reference", ".png", 16)
private object EsriImagery : EsriTiles("EsriWorldImagery", "World_Imagery", ".jpg", 18)

const val DARK_MAP_CREDIT = "Mapa © Esri, HERE, Garmin, © colaboradores do OpenStreetMap"
const val SATELLITE_CREDIT = "Imagens de satélite © Esri, Maxar, Earthstar Geographics"

/** Camada de nomes por cima do fundo (e por baixo de marcadores e polígonos, que entram depois). */
private class LabelsOverlay(map: MapView) : TilesOverlay(MapTileProviderBasic(map.context, EsriDarkLabels), map.context) {
    init {
        loadingBackgroundColor = AndroidColor.TRANSPARENT
        loadingLineColor = AndroidColor.TRANSPARENT
    }
}

/** Fundo escuro (ou satélite) com os nomes por cima. Pode ser chamado a cada atualização da tela. */
fun MapView.useArgosTiles(satellite: Boolean = false) {
    val source = if (satellite) EsriImagery else EsriDarkBase
    if (tileProvider.tileSource.name() != source.name()) setTileSource(source)
    if (overlays.none { it is LabelsOverlay }) overlays.add(0, LabelsOverlay(this))
    // Enquanto carrega, o quadriculado cinza-claro padrão piscava no meio do tema escuro.
    overlayManager.tilesOverlay.loadingBackgroundColor = 0xFF14161A.toInt()
    overlayManager.tilesOverlay.loadingLineColor = 0xFF14161A.toInt()
}
