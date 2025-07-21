package com.example.offlinelandareaapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.text.HtmlCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.*
import kotlin.math.abs
import kotlin.math.cos

class MainActivity : AppCompatActivity() {

    data class LatLng(val latitude: Double, val longitude: Double)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val points = mutableListOf<LatLng>()
    private var tracking = false
    private var paused = false
    private lateinit var adView: AdView

    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnStop: Button
    private lateinit var walkingMsg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Views
        btnStart = findViewById(R.id.btnStart)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        walkingMsg = findViewById(R.id.walkingMessage)

        // Start Button Click
        btnStart.setOnClickListener {
            walkingMsg.text = "Now you start walking 🚶‍♂️"
            walkingMsg.visibility = View.VISIBLE
            btnStart.visibility = View.GONE
            btnPause.visibility = View.VISIBLE
            btnStop.visibility = View.VISIBLE
            Toast.makeText(this, "Now you start walking 🚶‍♂️", Toast.LENGTH_SHORT).show()
            startTracking()
        }

        // Pause Button Click
        btnPause.setOnClickListener {
            paused = !paused
            btnPause.text = if (paused) "Resume" else "Pause"
            walkingMsg.text = if (paused) "Walking Paused ⏸" else "Now you start walking 🚶‍♂️"
        }

        // Stop Button Click
        btnStop.setOnClickListener {
            tracking = false
            paused = false
            walkingMsg.visibility = View.GONE
            btnPause.visibility = View.GONE
            btnStop.visibility = View.GONE
            btnStart.visibility = View.VISIBLE
            Toast.makeText(this, "Tracking Stopped ❌", Toast.LENGTH_SHORT).show()
            calculateAndShowArea()
        }

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}
        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    private fun startTracking() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000
        ).build()

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (tracking && !paused) {
                        for (loc in result.locations) {
                            points.add(LatLng(loc.latitude, loc.longitude))
                        }
                    }
                }
            },
            Looper.getMainLooper()
        )
    }

    private fun calculateAndShowArea() {
        val area = calculateArea(points)
        val gunthaExact = area / 101.17
        val gunthaRounded = gunthaExact.toInt()
        val displayText = "<b>Area: $gunthaRounded Guntha</b> (${area.toInt()} sq.m)"
        Toast.makeText(this, HtmlCompat.fromHtml(displayText, HtmlCompat.FROM_HTML_MODE_LEGACY), Toast.LENGTH_LONG).show()
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
}
