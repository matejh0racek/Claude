package com.matejhoracek.gtinav

import android.content.Context
import android.graphics.Color
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.fillExtrusionLayer
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources

// Everything that makes the map look like GTI Nav, shared by the phone and Android Auto.
object GtiLook {
    // Same as the iPhone app.
    const val RED = 0xFFE8122E.toInt()             // #E8122E
    const val ALTERNATE_GREY = 0xFF5A5A62.toInt()  // #5A5A62
    const val CARD = 0xE6121216.toInt()            // dark see-through card behind banners
    const val CAPTION = 0x8CFFFFFF.toInt()         // white at 55%

    // Dark map style bundled in assets (the same JSON as the iPhone app).
    const val STYLE_URI = "asset://gti-dark-style.json"

    // Test route: Brno hlavní nádraží → Výstaviště Brno.
    val TEST_ORIGIN: Point = Point.fromLngLat(16.6126, 49.1907)
    val TEST_DESTINATION: Point = Point.fromLngLat(16.5814, 49.1885)
    const val TEST_DESTINATION_NAME = "Výstaviště Brno"

    // Car size on screen. Make it bigger or smaller if the car looks wrong.
    const val PHONE_CAR_SCALE = 3.4f
    const val CAR_SCREEN_CAR_SCALE = 2.4f

    // White Polo GTI instead of the arrow.
    fun carPuck(scale: Float) = LocationPuck3D(
        modelUri = "asset://polo-gti.glb",
        modelScale = listOf(scale, scale, scale),
    )

    // Red route with a soft red glow; alternatives stay grey and there are no traffic colours.
    fun routeLineApi() = MapboxRouteLineApi(
        MapboxRouteLineApiOptions.Builder()
            .vanishingRouteLineEnabled(true)
            .build()
    )

    fun routeLineView(context: Context, style: Style): MapboxRouteLineView {
        val colors = RouteLineColorResources.Builder()
            .routeDefaultColor(RED)
            .routeLowCongestionColor(RED)
            .routeModerateCongestionColor(RED)
            .routeHeavyCongestionColor(RED)
            .routeSevereCongestionColor(RED)
            .routeUnknownCongestionColor(RED)
            .routeCasingColor(RED)
            .routeLineTraveledColor(Color.TRANSPARENT)
            .routeLineTraveledCasingColor(Color.TRANSPARENT)
            .alternativeRouteDefaultColor(ALTERNATE_GREY)
            .alternativeRouteLowCongestionColor(ALTERNATE_GREY)
            .alternativeRouteModerateCongestionColor(ALTERNATE_GREY)
            .alternativeRouteHeavyCongestionColor(ALTERNATE_GREY)
            .alternativeRouteSevereCongestionColor(ALTERNATE_GREY)
            .alternativeRouteUnknownCongestionColor(ALTERNATE_GREY)
            .alternativeRouteCasingColor(0xFF0A0A0C.toInt())
            .blurColor(RED)
            .build()
        val options = MapboxRouteLineViewOptions.Builder(context)
            .routeLineColorResources(colors)
            .routeLineBlurEnabled(true)
            .routeLineBlurWidth(22.0)
            .routeLineBlurOpacity(0.35)
            .routeLineBelowLayerId(roadLabelLayerId(style))
            .build()
        return MapboxRouteLineView(options).also { it.initializeLayers(style) }
    }

    private fun roadLabelLayerId(style: Style): String? =
        style.styleLayers.firstOrNull { it.id.contains("road-label") }?.id

    // Waze-like driver's view: always tilted, never flattening to top-down near turns, and close in.
    fun applyCamera(viewportDataSource: MapboxNavigationViewportDataSource) {
        with(viewportDataSource.options.followingFrameOptions) {
            defaultPitch = 62.0
            pitchNearManeuvers.enabled = false
            frameGeometryAfterManeuver.enabled = false
            minZoom = 16.4
            maxZoom = 18.4
        }
    }

    // Dark 3D buildings below the route and labels.
    fun add3DBuildings(style: Style) {
        if (style.styleLayerExists(BUILDINGS_LAYER_ID)) return
        val layer = fillExtrusionLayer(BUILDINGS_LAYER_ID, "composite") {
            sourceLayer("building")
            minZoom(14.0)
            filter(Expression.eq(Expression.get("extrude"), Expression.literal("true")))
            fillExtrusionColor(0xFF222228.toInt())
            fillExtrusionHeight(Expression.get("height"))
            fillExtrusionBase(Expression.get("min_height"))
            fillExtrusionOpacity(0.9)
        }
        if (style.styleLayerExists("road-major")) {
            style.addLayerAbove(layer, "road-major")
        } else {
            style.addLayer(layer)
        }
    }

    private const val BUILDINGS_LAYER_ID = "gti-3d-buildings"
}
