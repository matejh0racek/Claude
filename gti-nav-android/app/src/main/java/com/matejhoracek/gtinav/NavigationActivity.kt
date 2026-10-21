package com.matejhoracek.gtinav

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import java.util.Date
import kotlin.math.roundToInt

// The drive itself: map, turn banner, street name, and the time / ARRIVAL / distance card.
class NavigationActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TEST = "test"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LON = "lon"

        fun testDrive(context: Context) =
            Intent(context, NavigationActivity::class.java).putExtra(EXTRA_TEST, true)

        fun drive(context: Context, destination: Point) =
            Intent(context, NavigationActivity::class.java)
                .putExtra(EXTRA_LAT, destination.latitude())
                .putExtra(EXTRA_LON, destination.longitude())
    }

    private lateinit var mapView: MapView
    private lateinit var maneuverView: MapboxManeuverView
    private lateinit var maneuverCard: FrameLayout
    private lateinit var roadName: TextView
    private lateinit var bottomCard: LinearLayout
    private lateinit var timeValue: TextView
    private lateinit var arrivalValue: TextView
    private lateinit var distanceValue: TextView

    private val locationProvider = NavigationLocationProvider()
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var maneuverApi: MapboxManeuverApi
    private val routeLineApi: MapboxRouteLineApi = GtiLook.routeLineApi()
    private var routeLineView: MapboxRouteLineView? = null
    private var style: Style? = null

    // A real-GPS drive starts once the first position is known.
    private var pendingDestination: Point? = null
    private var hadRoute = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        buildViews()

        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(DistanceFormatterOptions.Builder(this).build())
        )

        mapView.compass.enabled = false
        mapView.scalebar.enabled = false
        mapView.location.apply {
            setLocationProvider(locationProvider)
            locationPuck = GtiLook.carPuck(GtiLook.PHONE_CAR_SCALE)
            puckBearingEnabled = true
            puckBearing = PuckBearing.COURSE
            enabled = true
        }
        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)
        GtiLook.applyCamera(viewportDataSource)
        navigationCamera = NavigationCamera(mapView.mapboxMap, mapView.camera, viewportDataSource)

        mapView.mapboxMap.loadStyle(GtiLook.STYLE_URI) { loaded ->
            style = loaded
            GtiLook.add3DBuildings(loaded)
            routeLineView = GtiLook.routeLineView(this, loaded)
            MapboxNavigationApp.current()?.let { renderRoutes(it) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = endDrive()
        })

        // The drive starts once the navigation engine is ready (see navigationObserver).
        shouldStartDrive = savedInstanceState == null
        MapboxNavigationApp.attach(this)
        MapboxNavigationApp.registerObserver(navigationObserver)
    }

    private var shouldStartDrive = false

    private fun startDrive(navigation: MapboxNavigation) {
        if (!shouldStartDrive) return
        shouldStartDrive = false
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            GtiDrive.startTestDrive(navigation, ::showError)
        } else {
            pendingDestination = Point.fromLngLat(
                intent.getDoubleExtra(EXTRA_LON, 0.0),
                intent.getDoubleExtra(EXTRA_LAT, 0.0),
            )
            GtiDrive.ensureRealGps(navigation)
        }
    }

    override fun onDestroy() {
        MapboxNavigationApp.unregisterObserver(navigationObserver)
        routeLineApi.cancel()
        routeLineView?.cancel()
        maneuverApi.cancel()
        super.onDestroy()
    }

    // MARK: - Engine events

    private val navigationObserver = object : MapboxNavigationObserver {
        override fun onAttached(mapboxNavigation: MapboxNavigation) {
            mapboxNavigation.registerLocationObserver(locationObserver)
            mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
            mapboxNavigation.registerRoutesObserver(routesObserver)
            mapboxNavigation.registerArrivalObserver(arrivalObserver)
            startDrive(mapboxNavigation)
        }

        override fun onDetached(mapboxNavigation: MapboxNavigation) {
            mapboxNavigation.unregisterLocationObserver(locationObserver)
            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
            mapboxNavigation.unregisterRoutesObserver(routesObserver)
            mapboxNavigation.unregisterArrivalObserver(arrivalObserver)
        }
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val location = locationMatcherResult.enhancedLocation
            locationProvider.changePosition(location, locationMatcherResult.keyPoints)
            viewportDataSource.onLocationChanged(location)
            viewportDataSource.evaluate()
            if (navigationCamera.state == NavigationCameraState.IDLE && !userMovedMap) {
                navigationCamera.requestNavigationCameraToFollowing()
            }

            val name = locationMatcherResult.road.components.joinToString(" ") { it.text }
            roadName.text = name
            roadName.visibility = if (name.isBlank()) View.INVISIBLE else View.VISIBLE

            pendingDestination?.let { destination ->
                pendingDestination = null
                MapboxNavigationApp.current()?.let {
                    GtiDrive.startDrive(
                        it, Point.fromLngLat(location.longitude, location.latitude), destination, ::showError
                    )
                }
            }
        }
    }

    private val routesObserver = RoutesObserver { result ->
        val navigation = MapboxNavigationApp.current() ?: return@RoutesObserver
        renderRoutes(navigation)
        val routes = result.navigationRoutes
        if (routes.isNotEmpty()) {
            hadRoute = true
            viewportDataSource.onRouteChanged(routes.first())
        } else {
            viewportDataSource.clearRouteData()
            // Ended from somewhere else (for example the car screen): close this screen too.
            if (hadRoute) finish()
        }
        viewportDataSource.evaluate()
    }

    private val routeProgressObserver = RouteProgressObserver { progress ->
        viewportDataSource.onRouteProgressChanged(progress)
        viewportDataSource.evaluate()

        style?.let { style ->
            routeLineApi.updateWithRouteProgress(progress) { result ->
                routeLineView?.renderRouteLineUpdate(style, result)
            }
        }
        maneuverView.renderManeuvers(maneuverApi.getManeuvers(progress))
        updateBottomCard(progress)
    }

    private val arrivalObserver = object : ArrivalObserver {
        override fun onWaypointArrival(routeProgress: RouteProgress) {}
        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {}
        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            Toast.makeText(this@NavigationActivity, "You have arrived", Toast.LENGTH_LONG).show()
            mapView.postDelayed({ endDrive() }, 5000)
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

    private fun endDrive() {
        MapboxNavigationApp.current()?.let { GtiDrive.endDrive(it) }
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    // MARK: - Bottom card

    private val timeFormat by lazy { DateFormat.getTimeFormat(this) }

    private fun updateBottomCard(progress: RouteProgress) {
        val seconds = progress.durationRemaining
        val minutes = (seconds / 60).roundToInt().coerceAtLeast(if (seconds > 0) 1 else 0)
        timeValue.text = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
        arrivalValue.text = timeFormat.format(Date(System.currentTimeMillis() + (seconds * 1000).toLong()))
        val metres = progress.distanceRemaining.toDouble()
        distanceValue.text = if (metres >= 1000) {
            String.format("%.1f km", metres / 1000)
        } else {
            "${((metres / 50).roundToInt() * 50)} m"
        }
    }

    // MARK: - Camera framing

    // Waze-style: the car sits centred, about 70% of the way down, and always above the street name.
    private var userMovedMap = false

    private fun updateCameraPadding() {
        val height = mapView.height
        if (height == 0) return
        val clearance = dp(70)
        val carY = minOf(height * 0.70, (roadName.top - clearance).toDouble())
        viewportDataSource.followingPadding = EdgeInsets(
            maneuverCard.bottom.toDouble(), 0.0, height - carY, 0.0
        )
        viewportDataSource.overviewPadding = EdgeInsets(
            maneuverCard.bottom + dp(40).toDouble(), dp(40).toDouble(),
            height - bottomCard.top + dp(40).toDouble(), dp(40).toDouble(),
        )
        viewportDataSource.evaluate()
    }

    // MARK: - Views

    private fun buildViews() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        mapView = MapView(this)
        root.addView(mapView, frameParams(MATCH, MATCH, Gravity.NO_GRAVITY))

        // Turn instruction at the top.
        maneuverView = MapboxManeuverView(this).apply {
            updateManeuverViewOptions(
                ManeuverViewOptions.Builder()
                    .maneuverBackgroundColor(android.R.color.transparent)
                    .subManeuverBackgroundColor(android.R.color.transparent)
                    .upcomingManeuverBackgroundColor(android.R.color.transparent)
                    .build()
            )
        }
        maneuverCard = FrameLayout(this).apply {
            background = cardBackground()
            clipToOutline = true
            addView(maneuverView, frameParams(MATCH, WRAP, Gravity.NO_GRAVITY))
        }
        root.addView(maneuverCard, frameParams(MATCH, WRAP, Gravity.TOP))

        // Round buttons on the right: route overview and end.
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(roundButton(R.drawable.ic_overview, "Route overview") { toggleOverview() })
            addView(roundButton(R.drawable.ic_close, "End navigation") { endDrive() })
        }
        root.addView(buttons, frameParams(WRAP, WRAP, Gravity.END or Gravity.TOP))

        // Bottom card: time · ARRIVAL · distance.
        timeValue = valueLabel(20f, Color.WHITE)
        arrivalValue = valueLabel(34f, GtiLook.RED)
        distanceValue = valueLabel(20f, Color.WHITE)
        bottomCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground()
            addView(column(timeValue, "time"), linearParams(0, WRAP, 1f))
            addView(column(arrivalValue, "ARRIVAL"), linearParams(0, WRAP, 1f))
            addView(column(distanceValue, "distance"), linearParams(0, WRAP, 1f))
        }
        root.addView(bottomCard, frameParams(MATCH, dp(84), Gravity.BOTTOM))

        // Street name, just above the bottom card.
        roadName = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            background = cardBackground(radiusDp = 20)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            visibility = View.INVISIBLE
        }
        root.addView(roadName, frameParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        setContentView(root)

        // Keep cards clear of the status bar and gesture bar.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            maneuverCard.setMargins(dp(8), bars.top + dp(8), dp(8), 0)
            buttons.setMargins(0, bars.top + dp(150), dp(12), 0)
            bottomCard.setMargins(dp(8), 0, dp(8), bars.bottom + dp(8))
            roadName.setMargins(0, 0, 0, bars.bottom + dp(84 + 16))
            root.requestLayout()
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateCameraPadding() }

        // Dragging the map stops following; the overview button brings the car back.
        mapView.gestures.addOnMoveListener(object : com.mapbox.maps.plugin.gestures.OnMoveListener {
            override fun onMoveBegin(detector: com.mapbox.android.gestures.MoveGestureDetector) {
                userMovedMap = true
                navigationCamera.requestNavigationCameraToIdle()
            }
            override fun onMove(detector: com.mapbox.android.gestures.MoveGestureDetector) = false
            override fun onMoveEnd(detector: com.mapbox.android.gestures.MoveGestureDetector) {}
        })
    }

    private fun toggleOverview() {
        userMovedMap = false
        if (navigationCamera.state == NavigationCameraState.FOLLOWING ||
            navigationCamera.state == NavigationCameraState.TRANSITION_TO_FOLLOWING
        ) {
            navigationCamera.requestNavigationCameraToOverview()
        } else {
            navigationCamera.requestNavigationCameraToFollowing()
        }
    }

    private fun roundButton(icon: Int, description: String, onClick: () -> Unit) =
        ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = description
            background = cardBackground(radiusDp = 28)
            setOnClickListener { onClick() }
            layoutParams = linearParams(dp(56), dp(56)).apply { bottomMargin = dp(12) }
        }

    private fun valueLabel(size: Float, color: Int) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        maxLines = 1
        text = "–"
    }

    private fun column(value: TextView, caption: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(value, linearParams(MATCH, WRAP))
        addView(TextView(context).apply {
            text = caption
            textSize = 11f
            setTextColor(GtiLook.CAPTION)
            gravity = Gravity.CENTER
        }, linearParams(MATCH, WRAP))
    }
}
