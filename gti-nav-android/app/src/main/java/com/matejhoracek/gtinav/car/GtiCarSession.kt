package com.matejhoracek.gtinav.car

import android.annotation.SuppressLint
import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mapbox.maps.ContextMode
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapOptions
import com.mapbox.maps.extension.androidauto.MapboxCarMap
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.androidauto.navigation.CarDistanceFormatter
import com.mapbox.navigation.ui.androidauto.navigation.CarNavigationInfoProvider
import com.mapbox.navigation.ui.androidauto.navigation.maneuver.CarManeuverMapper
import com.mapbox.navigation.ui.androidauto.navigation.roadlabel.CarRoadLabelRenderer
import com.matejhoracek.gtinav.GtiDrive
import com.matejhoracek.gtinav.GtiLook
import java.util.TimeZone

// Android Auto entry point.
class GtiCarAppService : CarAppService() {
    @SuppressLint("PrivateResource")
    override fun createHostValidator() = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession() = GtiCarSession()
}

class GtiCarSession : Session() {

    private val mapboxCarMap = MapboxCarMap()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                MapboxNavigationApp.attach(owner)
                MapboxNavigationApp.registerObserver(navigationObserver)
                mapboxCarMap.setup(
                    carContext,
                    MapInitOptions(
                        context = carContext,
                        mapOptions = MapOptions.Builder().contextMode(ContextMode.SHARED).build(),
                    ),
                )
                mapboxCarMap.registerObserver(GtiCarMapRenderer())
                mapboxCarMap.registerObserver(CarRoadLabelRenderer())
            }

            override fun onDestroy(owner: LifecycleOwner) {
                MapboxNavigationApp.unregisterObserver(navigationObserver)
                MapboxNavigationApp.detach(owner)
                mapboxCarMap.clearObservers()
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen = GtiCarScreen(this, mapboxCarMap)

    // MARK: - Telling the car a drive is running (for its cluster display and other apps)

    private val navigationManager by lazy { carContext.getCarService(NavigationManager::class.java) }
    private var maneuverApi: MapboxManeuverApi? = null
    private var isNavigating = false

    private val navigationObserver = object : MapboxNavigationObserver {
        override fun onAttached(mapboxNavigation: MapboxNavigation) {
            // The car needs a position even before a drive starts on the phone.
            GtiDrive.ensureRealGps(mapboxNavigation)
            maneuverApi = MapboxManeuverApi(
                MapboxDistanceFormatter(DistanceFormatterOptions.Builder(carContext).build())
            )
            navigationManager.setNavigationManagerCallback(object : NavigationManagerCallback {
                // Another navigation app took over: end this drive.
                override fun onStopNavigation() = GtiDrive.endDrive(mapboxNavigation)
            })
            mapboxNavigation.registerRoutesObserver(routesObserver)
            mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        }

        override fun onDetached(mapboxNavigation: MapboxNavigation) {
            mapboxNavigation.unregisterRoutesObserver(routesObserver)
            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
            maneuverApi?.cancel()
            maneuverApi = null
            if (isNavigating) navigationManager.navigationEnded()
            isNavigating = false
            navigationManager.clearNavigationManagerCallback()
        }
    }

    private val routesObserver = RoutesObserver { result ->
        if (result.navigationRoutes.isEmpty() && isNavigating) {
            isNavigating = false
            navigationManager.navigationEnded()
        } else if (result.navigationRoutes.isNotEmpty() && !isNavigating) {
            isNavigating = true
            navigationManager.navigationStarted()
        }
    }

    private val routeProgressObserver = RouteProgressObserver { progress ->
        val api = maneuverApi ?: return@RouteProgressObserver
        if (isNavigating) {
            runCatching { navigationManager.updateTrip(CarManeuverMapper.from(progress, api)) }
        }
    }
}

// The one car screen. While driving it shows the turn card and the arrival estimate;
// otherwise a hint to start from the phone. All controls stay on the phone.
class GtiCarScreen(session: Session, mapboxCarMap: MapboxCarMap) : Screen(session.carContext) {

    private val navigationInfoProvider = CarNavigationInfoProvider().invalidateOnChange(this)
    private var hasRoute = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                mapboxCarMap.registerObserver(navigationInfoProvider)
                MapboxNavigationApp.registerObserver(screenObserver)
            }

            override fun onPause(owner: LifecycleOwner) {
                mapboxCarMap.unregisterObserver(navigationInfoProvider)
                MapboxNavigationApp.unregisterObserver(screenObserver)
            }
        })
    }

    private val routesObserver = RoutesObserver { result ->
        val routed = result.navigationRoutes.isNotEmpty()
        if (routed != hasRoute) {
            hasRoute = routed
            invalidate()
        }
    }

    private var lastProgress: RouteProgress? = null
    private val routeProgressObserver = RouteProgressObserver { lastProgress = it }

    private val screenObserver = object : MapboxNavigationObserver {
        override fun onAttached(mapboxNavigation: MapboxNavigation) {
            mapboxNavigation.registerRoutesObserver(routesObserver)
            mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        }

        override fun onDetached(mapboxNavigation: MapboxNavigation) {
            mapboxNavigation.unregisterRoutesObserver(routesObserver)
            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        }
    }

    override fun onGetTemplate(): Template {
        val builder = NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.createCustom(GUIDANCE_BACKGROUND, GUIDANCE_BACKGROUND))
            // Android Auto requires at least one button here; this one does nothing.
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("GTI").setOnClickListener {}.build())
                    .build()
            )

        val info = navigationInfoProvider.carNavigationInfo.value
        val progress = lastProgress
        if (hasRoute && info.navigationInfo != null) {
            builder.setNavigationInfo(info.navigationInfo!!)
            progress?.let { builder.setDestinationTravelEstimate(redEstimate(it)) }
        } else {
            builder.setNavigationInfo(MessageInfo.Builder("Start a drive on your phone").build())
        }
        return builder.build()
    }

    // Time left in GTI red (the SDK's default is green).
    private fun redEstimate(progress: RouteProgress): TravelEstimate {
        val seconds = progress.durationRemaining.toLong()
        val arrival = DateTimeWithZone.create(
            System.currentTimeMillis() + seconds * 1000, TimeZone.getDefault()
        )
        return TravelEstimate.Builder(
            CarDistanceFormatter.carDistance(progress.distanceRemaining.toDouble()),
            arrival,
        )
            .setRemainingTimeSeconds(maxOf(seconds, 60))
            .setRemainingTimeColor(CarColor.createCustom(GtiLook.RED, GtiLook.RED))
            .build()
    }

    private companion object {
        const val GUIDANCE_BACKGROUND = 0xFF121216.toInt()
    }
}
