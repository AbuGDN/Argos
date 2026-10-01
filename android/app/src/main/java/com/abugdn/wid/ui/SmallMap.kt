package com.abugdn.wid.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Mapa osmdroid para telas menores (Sirenes, "E se fosse no Brasil?"), com as mesmas proteções
 * do MapScreen: sem destruir ao sair da tela, desmontado só no DisposableEffect, preso numa
 * moldura com clipChildren e com o `update` dentro de runCatching.
 */
@Composable
fun rememberSmallMap(center: GeoPoint, zoom: Double, minZoom: Double = 2.5, maxZoom: Double = 14.0): MapView {
    val context = LocalContext.current
    val map = remember { newSmallMap(context, center, zoom, minZoom, maxZoom) }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    return map
}

private fun newSmallMap(context: Context, center: GeoPoint, zoom: Double, minZoom: Double, maxZoom: Double): MapView {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = java.io.File(context.cacheDir, "osmdroid")
        osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles")
    }
    return MapView(context).apply {
        useArgosTiles()
        setMultiTouchControls(true)
        setDestroyMode(false)
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        setScrollableAreaLimitLatitude(80.0, -60.0, 0)
        minZoomLevel = minZoom
        maxZoomLevel = maxZoom
        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(zoom)
        controller.setCenter(center)
        setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }
}

@Composable
fun SmallMap(map: MapView, modifier: Modifier = Modifier, update: (MapView) -> Unit) {
    Box(modifier.clipToBounds()) {
        AndroidView(
            factory = { ctx ->
                (map.parent as? ViewGroup)?.removeView(map)
                FrameLayout(ctx).apply {
                    clipChildren = true
                    clipToPadding = true
                    addView(map, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                }
            },
            modifier = Modifier.matchParentSize().clipToBounds(),
            update = { _ -> runCatching { update(map); map.invalidate() } },
        )
        // A Esri pede o crédito visível no próprio mapa.
        Text(
            DARK_MAP_CREDIT,
            style = MaterialTheme.typography.labelSmall,
            color = Bone.copy(alpha = 0.7f),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).background(Ink.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
        )
    }
}
