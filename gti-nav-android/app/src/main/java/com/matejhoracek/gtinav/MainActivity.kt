package com.matejhoracek.gtinav

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Start screen: "Where to?" search and a test-drive button.
class MainActivity : AppCompatActivity() {

    private lateinit var searchField: EditText
    private lateinit var adapter: ArrayAdapter<String>
    private var results: List<Address> = emptyList()
    private var searchJob: Job? = null

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        buildViews()
        askForPermissions()
    }

    private fun askForPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // MARK: - Search

    private fun scheduleSearch(text: String) {
        searchJob?.cancel()
        if (text.isBlank()) {
            showResults(emptyList())
            return
        }
        searchJob = lifecycleScope.launch {
            delay(350) // wait until typing pauses
            showResults(search(text))
        }
    }

    // Android's built-in place search, preferring places within about 50 km of you.
    @Suppress("DEPRECATION")
    private suspend fun search(text: String): List<Address> = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(this@MainActivity)
        val here = lastKnownLocation()
        runCatching {
            if (here != null) {
                geocoder.getFromLocationName(
                    text, 8,
                    here.latitude() - 0.5, here.longitude() - 0.5,
                    here.latitude() + 0.5, here.longitude() + 0.5,
                ).orEmpty().ifEmpty { geocoder.getFromLocationName(text, 8).orEmpty() }
            } else {
                geocoder.getFromLocationName(text, 8).orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Point? {
        if (!hasLocationPermission()) return null
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time } ?: return null
        return Point.fromLngLat(location.longitude, location.latitude)
    }

    private fun showResults(addresses: List<Address>) {
        results = addresses
        adapter.clear()
        adapter.addAll(addresses.map { address ->
            (0..address.maxAddressLineIndex).joinToString(", ") { address.getAddressLine(it) }
                .ifBlank { address.featureName ?: "?" }
        })
    }

    private fun startDrive(address: Address) {
        if (!hasLocationPermission()) {
            askForPermissions()
            return
        }
        startActivity(
            NavigationActivity.drive(this, Point.fromLngLat(address.longitude, address.latitude))
        )
    }

    // MARK: - Views

    private fun buildViews() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val title = TextView(this).apply {
            text = "GTI Nav"
            textSize = 34f
            setTextColor(GtiLook.RED)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        root.addView(title, linearParams(WRAP, WRAP).apply { setMargins(dp(24), dp(16), 0, 0) })

        searchField = EditText(this).apply {
            hint = "Where to?"
            setHintTextColor(GtiLook.CAPTION)
            setTextColor(Color.WHITE)
            textSize = 20f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = cardBackground()
            setPadding(dp(20), 0, dp(20), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = scheduleSearch(s?.toString().orEmpty())
            })
        }
        root.addView(searchField, linearParams(MATCH, dp(56)).apply { setMargins(dp(16), dp(16), dp(16), dp(8)) })

        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup) =
                super.getView(position, convertView, parent).also {
                    (it as TextView).setTextColor(Color.WHITE)
                    it.textSize = 17f
                }
        }
        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = android.graphics.drawable.ColorDrawable(0x1FFFFFFF)
            dividerHeight = 1
            setOnItemClickListener { _, _, position, _ -> results.getOrNull(position)?.let(::startDrive) }
        }
        root.addView(list, linearParams(MATCH, 0, 1f))

        val testDrive = Button(this).apply {
            text = "Start test drive"
            isAllCaps = false
            textSize = 20f
            setTextColor(GtiLook.RED)
            typeface = Typeface.DEFAULT_BOLD
            background = null
            setOnClickListener { startActivity(NavigationActivity.testDrive(this@MainActivity)) }
        }
        root.addView(testDrive, linearParams(MATCH, dp(56)).apply { gravity = Gravity.CENTER })

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(0, bars.top, 0, bars.bottom + dp(8))
            insets
        }
    }
}
