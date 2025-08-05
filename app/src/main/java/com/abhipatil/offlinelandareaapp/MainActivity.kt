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
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
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

    // UI elements
    private lateinit var fabStart: FloatingActionButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnResume: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var walkingMsg: TextView
    private lateinit var mainResultText: TextView
    private lateinit var secondaryResultText: TextView
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

        // Initialize Views
        fabStart = findViewById(R.id.fabStart)
        btnPause = findViewById(R.id.btnPause)
        btnResume = findViewById(R.id.btnResume)
        btnStop = findViewById(R.id.btnStop)
        walkingMsg = findViewById(R.id.walkingMessage)
        mainResultText = findViewById(R.id.mainResultText)
        secondaryResultText = findViewById(R.id.secondaryResultText)
        toolbar = findViewById(R.id.toolbar)
        bottomAppBar = findViewById(R.id.bottomAppBar)

        // Set the toolbar as the activity's action bar
        setSupportActionBar(toolbar)

        // Set initial UI state
        mainResultText.text = "Press Start to measure area"
        secondaryResultText.visibility = View.GONE
        updateButtonVisibility()

        // --- ADMOB INITIALIZATION AND INITIAL LOAD ---
        MobileAds.initialize(this) {}
        adView = findViewById(R.id.adView)
        // Add AdListener for debugging ad loading
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() { Log.d("AdMob", "Ad loaded successfully!") }
            override fun onAdFailedToLoad(adError: LoadAdError) { Log.e("AdMob", "Ad failed to load: ${adError.message}") }
            override fun onAdOpened() { Log.d("AdMob", "Ad opened") }
            override fun onAdClicked() { Log.d("AdMob", "Ad clicked") }
            override fun onAdClosed() { Log.d("AdMob", "Ad closed") }
        }
        // Load the ad for the first time when the app starts
        loadBannerAd()
        // --- END ADMOB INITIALIZATION ---


        // FAB: Start Tracking
        fabStart.setOnClickListener {
            points.clear()
            tracking = true
            paused = false
            walkingMsg.text = " Now you start walking 🚶‍♂️ "
            walkingMsg.visibility = View.VISIBLE
            mainResultText.visibility = View.GONE
            secondaryResultText.visibility = View.GONE
            Toast.makeText(this, "Now you start walking 🚶‍♂️", Toast.LENGTH_SHORT).show()
            updateButtonVisibility()
            startTracking()

            Handler(Looper.getMainLooper()).postDelayed({
                if (tracking && !paused) {
                    walkingMsg.text = "🚶‍♂️🚶‍♂️🚶‍♂️"
                }
            }, 5000)
            
            // NEW: Load a fresh ad when the Start button is clicked
            loadBannerAd()
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
            walkingMsg.text = " Now you start walking 🚶 "
            Toast.makeText(this, "Tracking Resumed", Toast.LENGTH_SHORT).show()
            updateButtonVisibility()
            Handler(Looper.getMainLooper()).postDelayed({
                if (tracking && !paused) {
                    walkingMsg.text = "🚶‍♂️🚶‍♂️🚶‍♂️"
                }
            }, 5000)
        }

        // Button: Stop Tracking
        btnStop.setOnClickListener {
            tracking = false
            paused = false
            walkingMsg.visibility = View.GONE
            Toast.makeText(this, "Tracking Stopped. Calculating Area...", Toast.LENGTH_SHORT).show()
            stopTracking()
            calculateAndShowArea()
            updateButtonVisibility()

            // NEW: Load a fresh ad when the Stop button is clicked
            loadBannerAd()
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

    // NEW FUNCTION: Encapsulates the logic to load a banner ad
    private fun loadBannerAd() {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        Log.d("AdMob", "Ad request sent.")
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
                checkLocationServicesAndStartTracking()
                Toast.makeText(this, "Location permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location permission denied. Cannot track area.", Toast.LENGTH_LONG).show()
                tracking = false
                paused = false
                updateButtonVisibility()
                walkingMsg.visibility = View.GONE
            }
        }
    }

    private fun checkLocationServicesAndStartTracking() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(this, "Please enable location services (GPS) in your device settings to track area.", Toast.LENGTH_LONG).show()
            tracking = false
            paused = false
            updateButtonVisibility()
            walkingMsg.visibility = View.GONE
        } else {
            if (tracking) {
                startLocationUpdates()
            }
        }
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        checkLocationServicesAndStartTracking()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000
        ).build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationTracker", "Permissions not granted to start location updates.")
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        Log.d("LocationTracker", "Location tracking started.")
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationTracker", "Location tracking stopped. Total points: ${points.size}")
    }

    private fun calculateAndShowArea() {
        val area = calculateArea(points)
        val areaInAcre = area / 4046.86
        val gunthaExact = area / 101.17
        val gunthaRounded = gunthaExact.toInt()

        val mainText: String
        val secondaryText: String

        if (areaInAcre < 1) {
            mainText = if (gunthaRounded > 0) "$gunthaRounded Guntha" else "Less than 1 Guntha"
            secondaryText = "(${area.toInt()} sq.m)"
        } else {
            val acresPart = areaInAcre.toInt()
            val leftoverSqm = area - (acresPart * 4046.86)
            val leftoverGuntha = (leftoverSqm / 101.17).toInt()
            val acreText = if (acresPart == 1) "1 acre" else "$acresPart acres"
            val gunthaText = if (leftoverGuntha > 0) " $leftoverGuntha Guntha" else ""
            mainText = "$acreText$gunthaText"
            secondaryText = "(${area.toInt()} sq.m)"
        }

        mainResultText.text = mainText
        secondaryResultText.text = secondaryText
        mainResultText.visibility = View.VISIBLE
        secondaryResultText.visibility = View.VISIBLE
        Log.d("AreaCalculation", "Calculated Area: $mainText $secondaryText")
    }

    private fun calculateArea(coords: List<LatLng>): Double {
        if (coords.size < 3) return 0.0
        val metersPerLat = 111132.92
        val metersPerLon = 111319.49
        var area = 0.0
        for (i in coords.indices) {
            val j = (i + 1) % coords.size
            val x1 = (coords[i].longitude - coords[0].longitude) * metersPerLon * cos(Math.toRadians(coords[i].latitude))
            val y1 = (coords[i].latitude - coords[0].latitude) * metersPerLat
            val x2 = (coords[j].longitude - coords[0].longitude) * metersPerLon * cos(Math.toRadians(coords[j].latitude))
            val y2 = (coords[j].latitude - coords[0].latitude) * metersPerLat
            area += (x1 * y2) - (x2 * y1)
        }
        return abs(area / 2.0)
    }

    private fun updateButtonVisibility() {
        if (tracking) {
            fabStart.visibility = View.GONE
            bottomAppBar.visibility = View.VISIBLE
            mainResultText.visibility = View.GONE
            secondaryResultText.visibility = View.GONE
            walkingMsg.visibility = View.VISIBLE
            if (paused) {
                btnPause.visibility = View.GONE
                btnResume.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE
            } else {
                btnPause.visibility = View.VISIBLE
                btnResume.visibility = View.GONE
                btnStop.visibility = View.VISIBLE
            }
        } else {
            fabStart.visibility = View.VISIBLE
            bottomAppBar.visibility = View.GONE
            btnPause.visibility = View.GONE
            btnResume.visibility = View.GONE
            btnStop.visibility = View.GONE
            walkingMsg.visibility = View.GONE
        }
    }
}