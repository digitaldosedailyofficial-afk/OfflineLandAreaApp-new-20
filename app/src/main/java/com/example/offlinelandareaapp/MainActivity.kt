package com.example.offlinelandareaapp

import android.Manifest
import android.content.Context // Import Context for LocationManager
import android.content.pm.PackageManager
import android.location.LocationManager // Import LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log // Import Log for debugging
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.text.HtmlCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError // Import LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.*
import kotlin.math.abs
import kotlin.math.cos
import android.os.Handler // Import Handler
import android.content.Intent // Import Intent for settings

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

    // UI elements
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnResume: Button
    private lateinit var btnStop: Button
    private lateinit var walkingMsg: TextView
    private lateinit var resultText: TextView // New TextView for displaying results

    // LocationCallback for receiving location updates
    private lateinit var locationCallback: LocationCallback

    // Request code for location permissions
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Views
        btnStart = findViewById(R.id.btnStart)
        btnPause = findViewById(R.id.btnPause)
        btnResume = findViewById(R.id.btnResume)
        btnStop = findViewById(R.id.btnStop)
        walkingMsg = findViewById(R.id.walkingMessage)
        resultText = findViewById(R.id.resultText) // Initialize the new TextView

        // Set initial UI state
        // Set initial text for resultText here, not in updateButtonVisibility
        resultText.text = "Press Start to measure area"
        updateButtonVisibility()

        // Button: Start Tracking
        btnStart.setOnClickListener {
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

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}
        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        // Add AdListener for debugging ad loading
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Code to be executed when an ad finishes loading.
                Log.d("AdMob", "Ad loaded successfully!")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                // Code to be executed when an ad request fails.
                Log.e("AdMob", "Ad failed to load: ${adError.message}")
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
            // Optionally, you can prompt the user to go to settings
            // val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            // startActivity(intent)
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


    // Starts receiving location updates
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
                "<b><font color='black'>Area: $gunthaRounded Guntha</font></b> (<font color='black'>${area.toInt()} sq.m</font>)"
            } else {
                "<b><font color='black'>Area: Less than 1 Guntha</font></b> (<font color='black'>${area.toInt()} sq.m</font>)"
            }
        } else {
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
        val metersPerLon = 111319.49 // Approximate meters per degree longitude at equator

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
            btnStart.visibility = View.GONE
            btnStop.visibility = View.VISIBLE
            if (paused) {
                btnPause.visibility = View.GONE
                btnResume.visibility = View.VISIBLE
            } else {
                btnPause.visibility = View.VISIBLE
                btnResume.visibility = View.GONE
            }
        } else { // When not tracking (initial state or after stop)
            btnStart.visibility = View.VISIBLE
            btnPause.visibility = View.GONE
            btnResume.visibility = View.GONE
            btnStop.visibility = View.GONE
            // Do NOT reset resultText.text here. calculateAndShowArea() handles it.
            // resultText.visibility is also handled by calculateAndShowArea() or initial setup.
        }
    }
}
