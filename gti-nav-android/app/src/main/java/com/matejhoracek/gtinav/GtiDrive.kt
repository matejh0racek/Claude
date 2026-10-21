package com.matejhoracek.gtinav

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.TripSessionState

// Starts and ends drives. There is one navigation engine (MapboxNavigationApp) shared by the
// phone screen and Android Auto, so a drive started on the phone shows in the car too.
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
object GtiDrive {

    // True while a simulated test drive is running instead of real GPS.
    var isTestDrive = false
        private set

    // Real GPS. Starts the location updates if nothing is running yet.
    @SuppressLint("MissingPermission")
    fun ensureRealGps(navigation: MapboxNavigation) {
        if (isTestDrive || navigation.getTripSessionState() == TripSessionState.STARTED) return
        val context = navigation.navigationOptions.applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return // the phone app asks for location access on first start
        navigation.startTripSession()
    }

    // Real GPS drive from where you are now.
    fun startDrive(
        navigation: MapboxNavigation,
        origin: Point,
        destination: Point,
        onError: (String) -> Unit,
    ) {
        stopTestDrive(navigation)
        ensureRealGps(navigation)
        requestRoute(navigation, origin, destination, onError) { routes ->
            navigation.setNavigationRoutes(routes)
        }
    }

    // Simulated drive from Brno hlavní nádraží to Výstaviště, so you can test at your desk.
    @SuppressLint("MissingPermission")
    fun startTestDrive(navigation: MapboxNavigation, onError: (String) -> Unit) {
        navigation.stopTripSession()
        navigation.mapboxReplayer.stop()
        navigation.mapboxReplayer.clearEvents()
        navigation.startReplayTripSession()
        isTestDrive = true
        requestRoute(navigation, GtiLook.TEST_ORIGIN, GtiLook.TEST_DESTINATION, onError) { routes ->
            navigation.setNavigationRoutes(routes)
            val events = ReplayRouteMapper().mapDirectionsRouteGeometry(routes.first().directionsRoute)
            navigation.mapboxReplayer.pushEvents(events)
            navigation.mapboxReplayer.seekTo(events.first())
            navigation.mapboxReplayer.play()
        }
    }

    // Ends the drive on the phone and in the car.
    fun endDrive(navigation: MapboxNavigation) {
        navigation.setNavigationRoutes(emptyList())
        stopTestDrive(navigation)
    }

    private fun stopTestDrive(navigation: MapboxNavigation) {
        if (!isTestDrive) return
        isTestDrive = false
        navigation.mapboxReplayer.stop()
        navigation.mapboxReplayer.clearEvents()
        navigation.stopTripSession()
        ensureRealGps(navigation)
    }

    private fun requestRoute(
        navigation: MapboxNavigation,
        origin: Point,
        destination: Point,
        onError: (String) -> Unit,
        onReady: (List<NavigationRoute>) -> Unit,
    ) {
        val formatter = navigation.navigationOptions.distanceFormatterOptions
        val options = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .language(formatter.locale.language)
            .voiceUnits(
                if (formatter.unitType == UnitType.IMPERIAL) DirectionsCriteria.IMPERIAL
                else DirectionsCriteria.METRIC
            )
            .alternatives(true)
            .coordinatesList(listOf(origin, destination))
            .build()
        navigation.requestRoutes(options, object : NavigationRouterCallback {
            override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                if (routes.isEmpty()) onError("No route found") else onReady(routes)
            }

            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                onError(reasons.firstOrNull()?.message ?: "No route found")
            }

            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {}
        })
    }
}
