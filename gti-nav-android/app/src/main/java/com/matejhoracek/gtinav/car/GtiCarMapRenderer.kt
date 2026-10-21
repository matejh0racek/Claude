package com.matejhoracek.gtinav.car

import android.graphics.Rect
import com.mapbox.common.location.Location
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.extension.androidauto.MapboxCarMapObserver
import com.mapbox.maps.extension.androidauto.MapboxCarMapSurface
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.androidauto.location.CarLocationProvider
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.matejhoracek.gtinav.GtiLook

// Draws the GTI map on the car screen: dark style, 3D buildings, red glowing route,
// white Polo and the Waze-like following camera.
class GtiCarMapRenderer : MapboxCarMapObserver {

    private var surface: MapboxCarMapSurface? = null
    private var style: Style? = null
    private val routeLineApi: MapboxRouteLineApi = GtiLook.routeLineApi()
    private var routeLineView: MapboxRouteLineView? = null
    private var viewportDataSource: MapboxNavigationViewportDataSource? = null
    private var navigationCamera: NavigationCamera? = null

    override fun onAttached(mapboxCarMapSurface: MapboxCarMapSurface) {
        surface = mapboxCarMapSurface
        val mapSurface = mapboxCarMapSurface.mapSurface
        val mapboxMap = mapSurface.mapboxMap

        mapboxMap.loadStyle(GtiLook.STYLE_URI) { loaded ->
            style = loaded
            GtiLook.add3DBuildings(loaded)
            routeLineView = GtiLook.routeLineView(mapboxCarMapSurface.carContext, loaded)
            MapboxNavigationApp.current()?.let { renderRoutes(it) }
        }

        mapSurface.location.apply {
            locationPuck = GtiLook.carPuck(GtiLook.CAR_SCREEN_CAR_SCALE)
            puckBearingEnabled = true
            puckBearing = PuckBearing.COURSE
            setLocationProvider(CarLocationProvider.getRegisteredInstance())
            enabled = true
        }

        val dataSource = MapboxNavigationViewportDataSource(mapboxMap)
        GtiLook.applyCamera(dataSource)
        viewportDataSource = dataSource
        navigationCamera = NavigationCamera(mapboxMap, mapSurface.camera, dataSource)

        MapboxNavigationApp.registerObserver(navigationObserver)
    }

    override fun onDetached(mapboxCarMapSurface: MapboxCarMapSurface) {
        MapboxNavigationApp.unregisterObserver(navigationObserver)
        routeLineApi.cancel()
        routeLineView?.cancel()
        routeLineView = null
        style = null
        viewportDataSource = null
        navigationCamera = null
        surface = null
    }

    // Like Waze: the car sits in the lower third of the visible map, showing the road ahead.
    override fun onVisibleAreaChanged(visibleArea: Rect, edgeInsets: EdgeInsets) {
        val dataSource = viewportDataSource ?: return
        val visibleHeight = (visibleArea.bottom - visibleArea.top).toDouble()
        dataSource.followingPadding = EdgeInsets(
            edgeInsets.top, edgeInsets.left, edgeInsets.bottom + visibleHeight / 3, edgeInsets.right
        )
        dataSource.overviewPadding = EdgeInsets(
            edgeInsets.top + 20, edgeInsets.left + 20, edgeInsets.bottom + 20, edgeInsets.right + 20
        )
        dataSource.evaluate()
    }

    private val navigationObserver = object : MapboxNavigationObserver {
        override fun onAttached(mapboxNavigation: MapboxNavigation) {
            mapboxNavigation.registerLocationObserver(locationObserver)
            mapboxNavigation.registerRoutesObserver(routesObserver)
            mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        }

        override fun onDetached(mapboxNavigation: MapboxNavigation) {
            mapboxNavigation.unregisterLocationObserver(locationObserver)
            mapboxNavigation.unregisterRoutesObserver(routesObserver)
            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        }
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val dataSource = viewportDataSource ?: return
            dataSource.onLocationChanged(locationMatcherResult.enhancedLocation)
            dataSource.evaluate()
            if (navigationCamera?.state == NavigationCameraState.IDLE) {
                navigationCamera?.requestNavigationCameraToFollowing()
            }
        }
    }

    private val routesObserver = RoutesObserver { result ->
        MapboxNavigationApp.current()?.let { renderRoutes(it) }
        val dataSource = viewportDataSource ?: return@RoutesObserver
        val routes = result.navigationRoutes
        if (routes.isEmpty()) dataSource.clearRouteData() else dataSource.onRouteChanged(routes.first())
        dataSource.evaluate()
    }

    private val routeProgressObserver = RouteProgressObserver { progress ->
        viewportDataSource?.let {
            it.onRouteProgressChanged(progress)
            it.evaluate()
        }
        val style = style ?: return@RouteProgressObserver
        routeLineApi.updateWithRouteProgress(progress) { result ->
            routeLineView?.renderRouteLineUpdate(style, result)
        }
    }

    private fun renderRoutes(navigation: MapboxNavigation) {
        val style = style ?: return
        val view = routeLineView ?: return
        val routes = navigation.getNavigationRoutes()
        if (routes.isEmpty()) {
            routeLineApi.clearRouteLine { view.renderClearRouteLineValue(style, it) }
        } else {
            routeLineApi.setNavigationRoutes(routes, navigation.getAlternativeMetadataFor(routes)) {
                view.renderRouteDrawData(style, it)
            }
        }
    }
}
