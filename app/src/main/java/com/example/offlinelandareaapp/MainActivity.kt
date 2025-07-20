package com.example.offlinelandareaapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val points = mutableListOf<LatLng>()
    private var tracking = false
    private var paused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val startButton = findViewById<Button>(R.id.startButton)
        val pauseButton = findViewById<Button>(R.id.pauseButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val resultText = findViewById<TextView>(R.id.resultText)

        startButton.setOnClickListener {
            points.clear()
            tracking = true
            paused = false
            startButton.visibility = View.GONE
            pauseButton.visibility = View.VISIBLE
            stopButton.visibility = View.VISIBLE
            resultText.text = "" // Hide "Press Start"
            startTracking()
        }

        pauseButton.setOnClickListener {
            paused = !paused
            pauseButton.text = if (paused) "Resume" else "Pause"
        }

        stopButton.setOnClickListener {
            tracking = false
            paused = false
            stopButton.visibility = View.GONE
            pauseButton.visibility = View.GONE
            startButton.visibility = View.VISIBLE

            val area = calculateArea(points)
            val gunthaExact = area / 101.17

            val gunthaRounded = if ((gunthaExact - floor(gunthaExact)) >= 0.50) {
                ceil(gunthaExact).toInt()
            } else {
                floor(gunthaExact).toInt()
            }

            val resultTextString = if (gunthaRounded > 0) {
                "<b>Area: $gunthaRounded Guntha</b> (${area.toInt()} sq.m)"
            } else {
                "<b>Area: Less than 1 Guntha</b> (${area.toInt()} sq.m)"
            }

            resultText.text = android.text.Html.fromHtml(
                resultTextString,
                android.text.Html.FROM_HTML_MODE_LEGACY
            )
        }
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

    private fun calculateArea(coords: List<LatLng>): Double {
        if (coords.size < 3) return 0.0
        // Shoelace formula
        var area = 0.0
        for (i in coords.indices) {
            val j = (i + 1) % coords.size
            area += coords[i].latitude * coords[j].longitude
            area -= coords[j].latitude * coords[i].longitude
        }
        return abs(area / 2.0) * 111320 * 111320
    }

    data class LatLng(val latitude: Double, val longitude: Double)
}
