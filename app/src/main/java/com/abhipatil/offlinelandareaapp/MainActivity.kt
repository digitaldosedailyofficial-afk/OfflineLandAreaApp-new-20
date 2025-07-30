package com.abhipatil.offlinelandareaapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.text.HtmlCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize // Import AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs
import kotlin.math.cos

class MainActivity : AppCompatActivity() {

    // Data class to hold latitude and longitude
    data class LatLng(val latitude: Double, val longitude: Double)

    // FusedLocationProviderClient for location services
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // List to store location points
    private val points = mutableListOf<LatLng>()
    // Tracking state variables
    private var tracking = false
    private var paused = false
    // AdView instance
    private lateinit var adView: AdView

    // UI elements - Updated to Material Design components
    private lateinit var fabStart: FloatingActionButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnResume: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var walkingMsg: TextView
    private lateinit var resultText: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomAppBar: BottomAppBar

    // LocationCallback for receiving location updates
    private lateinit var locationCallback: LocationCallback

    // Request code for location permissions
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Views - Now referencing the new Material Design IDs
        fabStart = findViewById(R.id.fabStart)
        btnPause = findViewById(R.id.btnPause)
        btnResume = findViewById(R.id.btnResume)
        btnStop = findViewById(R.id.btnStop)
        walkingMsg = findViewById(R.id.walkingMessage)
        resultText = findViewById(R.id.resultText)
        toolbar = findViewById(R.id.toolbar)
        bottomAppBar = findViewById(R.id.bottomAppBar)

        // Set the toolbar as the activity's action bar
        setSupportActionBar(toolbar)

        // Set initial UI state
        resultText.text = "Press Start to measure area"
        updateButtonVisibility()

        // FAB: Start Tracking
        fabStart.setOnClickListener {
            points.clear() // Clear previous points for a new measurement
            tracking = true
            paused = false
            walkingMsg.text = "Now you start walking 🚶‍♂️" // Initial text with symbol
            walkingMsg.visibility = View.VISIBLE
            resultText.visibility = View.GONE // Hide result text when tracking starts
            Toast.makeText(this, "Now you start walking 🚶‍♂️", Toast.LENGTH_SHORT).show()
            updateButtonVisibility()
            startTracking() // This will now include the location service check

            // Hide "Now you start walking" text after 5 seconds, leaving only the symbol
            Handler(Looper.getMainLooper()).postDelayed({
                if (tracking && !paused) { // Only change if still tracking and not paused
                    walkingMsg.text = "🚶‍♂️"
                }
            }, 5000) // 5000 milliseconds = 5 seconds
        }

        // Button: Pause Tracking
        btnPause.setOnClickListener {
            paused = true
            walkingMsg.text = "Walking Paused ⏸"
            Toast.makeText(this, "Tracking Paused", Toast.LENGTH_SHORT).show()
            updateButtonVisibility()
        }

        // Button: Resume Tracking
        btnResume.setOnClickListener {
            paused = false
            walkingMsg.text = "Now you start walking 🚶‍♂️"
            Toast.makeText(this, "Tracking Resumed", Toast.LENGTH_SHORT).show()
            updateButtonVisibility()
            // If resuming, and the text was previously hidden, show the full text again briefly
            Handler(Looper.getMainLooper()).postDelayed({
                if (tracking && !paused) {
                    walkingMsg.text = "🚶‍♂️"
                }
            }, 5000)
        }

        // Button: Stop Tracking
        btnStop.setOnClickListener {
            tracking = false
            paused = false // Reset paused state
            walkingMsg.visibility = View.GONE
            Toast.makeText(this, "Tracking Stopped. Calculating Area...", Toast.LENGTH_SHORT).show()
            stopTracking() // Stop location updates
            calculateAndShowArea() // Calculate and display the area
            updateButtonVisibility() // Update button visibility after calculation
        }

        // --- ADMOB INITIALIZATION AND FIX ---
        // Initialize Mobile Ads SDK (recommended to do this once per app, e.g., in Application class)
        MobileAds.initialize(this) {}

        adView = findViewById(R.id.adView)

        // *** FIX: Set Ad Unit ID and Ad Size programmatically here ***
        // This ensures they are set before loadAd() and prevents duplicate settings from XML.
        adView.adUnitId = getString(R.string.banner_ad_unit_id)
        adView.setAdSize(AdSize.BANNER)

        // Create an AdRequest
        val adRequest = AdRequest.Builder().build()

        // Load the ad
        adView.loadAd(adRequest)

        // Add AdListener for debugging ad loading
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Code to be executed when an ad finishes loading.
                Log.d("AdMob", "Ad loaded successfully!")
                Toast.makeText(this@MainActivity, "Ad loaded!", Toast.LENGTH_SHORT).show()
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                // Code to be executed when an ad request fails.
                Log.e("AdMob", "Ad failed to load: ${adError.message} (Code: ${adError.code})")
                Toast.makeText(this@MainActivity, "Ad failed to load: ${adError.message}", Toast.LENGTH_LONG).show()
            }

            override fun onAdOpened() {
                // Code to be executed when an ad opens an overlay that covers the screen.
                Log.d("AdMob", "Ad opened")
            }

            override fun onAdClicked() {
                // Code to be executed when the user clicks on an ad.
                Log.d("AdMob", "Ad clicked")
            }

            override fun onAdClosed() {
                // Code to be executed when the user is about to return to the app after clicking on an ad.
                Log.d("AdMob", "Ad closed")
            }
        }
        // --- END ADMOB INITIALIZATION AND FIX ---


        // Define the LocationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (tracking && !paused) {
                    for (loc in result.locations) {
                        points.add(LatLng(loc.latitude, loc.longitude))
                        Log.d("LocationTracker", "Point added: ${loc.latitude}, ${loc.longitude}")
                    }
                }
            }
        }
    }

    // Handles runtime permission requests
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, now check if location services are enabled
                checkLocationServicesAndStartTracking()
                Toast.makeText(this, "Location permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                // Permission denied
                Toast.makeText(this, "Location permission denied. Cannot track area.", Toast.LENGTH_LONG).show()
                // Reset UI if permission is denied
                tracking = false
                paused = false
                updateButtonVisibility()
                walkingMsg.visibility = View.GONE
            }
        }
    }

    // New function to check if location services are enabled
    private fun checkLocationServicesAndStartTracking() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            // Location services are disabled
            Toast.makeText(this, "Please enable location services (GPS) in your device settings to track area.", Toast.LENGTH_LONG).show()
            // Reset tracking state if location services are not enabled
            tracking = false
            paused = false
            updateButtonVisibility()
            walkingMsg.visibility = View.GONE
        } else {
            // Location services are enabled, proceed to start tracking
            if (tracking) { // Only start if tracking was intended to be true
                startLocationUpdates()
            }
        }
    }

    // Starts receiving location updates (handles permissions and service checks)
    private fun startTracking() {
        // First, check for permissions
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permissions if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        // Permissions are granted, now check if location services are enabled
        checkLocationServicesAndStartTracking()
    }

    // Renamed from startTracking to startLocationUpdates to avoid confusion with the permission/service check
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000 // Update every 2 seconds
        ).build()

        // It's good practice to re-check permissions immediately before requesting updates,
        // though `startTracking()` should largely handle this.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // This case should ideally not be reached if startTracking() is properly used,
            // but is a safe guard.
            Log.e("LocationTracker", "Permissions not granted to start location updates.")
            return
        }

        // Request location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback, // Use the defined locationCallback
            Looper.getMainLooper()
        )
        Log.d("LocationTracker", "Location tracking started.")
    }

    // Stops receiving location updates
    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationTracker", "Location tracking stopped. Total points: ${points.size}")
    }

    // Calculates the area and displays it on the resultText TextView
    private fun calculateAndShowArea() {
        val area = calculateArea(points) // area in sq.m
        val areaInAcre = area / 4046.86 // 1 acre = 4046.86 sq.m
        val gunthaExact = area / 101.17 // 1 guntha = 101.17 sq.m
        val gunthaRounded = gunthaExact.toInt()

        val displayText = if (areaInAcre < 1) {
            if (gunthaRounded > 0) {
                // Display Guntha directly if less than 1 acre
                "<b><font color='black'>Area: $gunthaRounded Guntha</font></b> (<font color='black'>${area.toInt()} sq.m</font>)"
            } else {
                // Handle very small areas less than 1 Guntha
                "<b><font color='black'>Area: Less than 1 Guntha</font></b> (<font color='black'>${area.toInt()} sq.m</font>)"
            }
        } else {
            // Calculate acres and remaining guntha for areas >= 1 acre
            val acresPart = areaInAcre.toInt()
            val leftoverSqm = area - (acresPart * 4046.86)
            val leftoverGuntha = (leftoverSqm / 101.17).toInt()

            val acreText = if (acresPart == 1) "1 acre" else "$acresPart acres"
            val gunthaText = if (leftoverGuntha > 0) " $leftoverGuntha Guntha" else ""

            "<b><font color='black'>Area: $acreText$gunthaText</font></b> (<font color='black'>${area.toInt()} sq.m</font>)"
        }

        resultText.text = HtmlCompat.fromHtml(displayText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        resultText.visibility = View.VISIBLE // Make result text visible
        Log.d("AreaCalculation", "Calculated Area: $displayText")
    }

    // Calculates the area of a polygon given a list of LatLng points (using Shoelace formula approximation)
    private fun calculateArea(coords: List<LatLng>): Double {
        if (coords.size < 3) return 0.0 // Need at least 3 points for a polygon

        val metersPerLat = 111132.92 // Approximate meters per degree latitude
        // metersPerLon depends on latitude, this is an approximation at equator
        // A more accurate calculation would adjust metersPerLon based on the average latitude of the points
        val metersPerLon = 111319.49

        var area = 0.0
        // Iterate through points to apply Shoelace formula
        for (i in coords.indices) {
            val j = (i + 1) % coords.size // Next point in the polygon (wraps around)

            // Convert LatLng to approximate Cartesian coordinates (relative to the first point)
            // Longitude conversion needs cosine of latitude to account for convergence of meridians
            val x1 = (coords[i].longitude - coords[0].longitude) * metersPerLon * cos(Math.toRadians(coords[i].latitude))
            val y1 = (coords[i].latitude - coords[0].latitude) * metersPerLat

            val x2 = (coords[j].longitude - coords[0].longitude) * metersPerLon * cos(Math.toRadians(coords[j].latitude))
            val y2 = (coords[j].latitude - coords[0].latitude) * metersPerLat

            area += (x1 * y2) - (x2 * y1) // Part of the Shoelace formula
        }
        return abs(area / 2.0) // Return absolute half of the sum
    }

    // Helper function to update button visibility based on tracking and paused states
    private fun updateButtonVisibility() {
        if (tracking) {
            fabStart.visibility = View.GONE // Hide FAB when tracking starts
            bottomAppBar.visibility = View.VISIBLE // Show BottomAppBar with controls

            if (paused) {
                btnPause.visibility = View.GONE
                btnResume.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE // Stop should always be visible when paused
            } else {
                btnPause.visibility = View.VISIBLE
                btnResume.visibility = View.GONE
                btnStop.visibility = View.VISIBLE // Stop should always be visible when tracking
            }
        } else { // When not tracking (initial state or after stop)
            fabStart.visibility = View.VISIBLE // Show FAB for new measurement
            bottomAppBar.visibility = View.GONE // Hide BottomAppBar
            btnPause.visibility = View.GONE      // Ensure individual buttons are hidden too
            btnResume.visibility = View.GONE
            btnStop.visibility = View.GONE
            // resultText visibility is handled by calculateAndShowArea() or initial setup.
        }
    }
}