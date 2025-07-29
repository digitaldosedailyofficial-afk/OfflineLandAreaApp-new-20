package com.abhipatil.offlinelandareaapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
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
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton // Import MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton // Import FloatingActionButton
import kotlin.math.abs
import kotlin.math.cos
import android.os.Handler
import com.google.android.material.appbar.MaterialToolbar // Import MaterialToolbar

class MainActivity : AppCompatActivity() {

    data class LatLng(val latitude: Double, val longitude: Double)

    private lateinit var fusedLocationClient: FusedLocationClient
    private val points = mutableListOf<LatLng>()
    private var tracking = false
    private var paused = false
    private lateinit var adView: AdView

    // UI elements
    private lateinit var fabStart: FloatingActionButton // Changed to FAB
    private lateinit var btnPause: MaterialButton // Changed to MaterialButton
    private lateinit var btnResume: MaterialButton // Changed to MaterialButton
    private lateinit var btnStop: MaterialButton // Changed to MaterialButton
    private lateinit var walkingMsg: TextView
    private lateinit var resultText: TextView
    private lateinit var toolbar: MaterialToolbar // Added Toolbar

    private lateinit var locationCallback: LocationCallback

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Views
        fabStart = findViewById(R.id.fabStart) // Reference the FAB
        btnPause = findViewById(R.id.btnPause)
        btnResume = findViewById(R.id.btnResume)
        btnStop = findViewById(R.id.btnStop)
        walkingMsg = findViewById(R.id.walkingMessage)
        resultText = findViewById(R.id.resultText)
        toolbar = findViewById(R.id.toolbar) // Initialize Toolbar

        setSupportActionBar(toolbar) // Set the toolbar as the app's action bar

        resultText.text = "Press Start to measure area"
        updateButtonVisibility()

        // FAB: Start Tracking
        fabStart.setOnClickListener {
            points.clear()
            tracking = true
            paused = false
            walkingMsg.text = "Now you start walking 🚶‍♂️"
            walkingMsg.visibility = View.VISIBLE
            resultText.visibility = View.GONE
            Toast.makeText(this, "Now you start walking 🚶‍♂️", Toast.LENGTH_SHORT).show()
            updateButtonVisibility()
            startTracking()

            Handler(Looper.getMainLooper()).postDelayed({
                if (tracking && !paused) {
                    walkingMsg.text = "🚶‍♂️" // Keep only the symbol
                }
            }, 5000)
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
            Handler(Looper.getMainLooper()).postDelayed({
                if (tracking && !paused) {
                    walkingMsg.text = "🚶‍♂️"
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
        }

        MobileAds.initialize(this) {}
        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdMob", "Ad loaded successfully!")
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("AdMob", "Ad failed to load: ${adError.message}")
            }
            override fun onAdOpened() {
                Log.d("AdMob", "Ad opened")
            }
            override fun onAdClicked() {
                Log.d("AdMob", "Ad clicked")
            }
            override fun onAdClosed() {
                Log.d("AdMob", "Ad closed")
            }
        }

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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // This check should ideally be handled by the startTracking() method
            // but keeping it here as a failsafe or if this method is called directly
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
        resultText.visibility = View.VISIBLE
        Log.d("AreaCalculation", "Calculated Area: $displayText")
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
            fabStart.visibility = View.GONE // Hide FAB when tracking
            btnStop.visibility = View.VISIBLE
            if (paused) {
                btnPause.visibility = View.GONE
                btnResume.visibility = View.VISIBLE
            } else {
                btnPause.visibility = View.VISIBLE
                btnResume.visibility = View.GONE
            }
        } else { // When not tracking (initial state or after stop)
            fabStart.visibility = View.VISIBLE // Show FAB when not tracking
            btnPause.visibility = View.GONE
            btnResume.visibility = View.GONE
            btnStop.visibility = View.GONE
        }
    }
}