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
import kotlin.math.floor
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val points = mutableListOf<LatLng>()
    private var tracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val resultText = findViewById<TextView>(R.id.resultText)

        startButton.setOnClickListener {
            points.clear()
            tracking = true
            startButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
            startTracking()
        }

        stopButton.setOnClickListener {
            tracking = false
            stopButton.visibility = View.GONE
            startButton.visibility = View.VISIBLE

            val area = calculateArea(points) // sq.m

            val guntaExact = area / 101.17

            // Custom rounding: if fractional part >= 0.50 round up, else round down
            val guntaRounded = if ((guntaExact - floor(guntaExact)) >= 0.50) {
                ceil(guntaExact).toInt()
            } else {
                floor(guntaExact).toInt()
            }

            val resultTextString = if (guntaRounded > 0) {
                "<b>Area: $guntaRounded guntaa</b> (${area.toInt()} sq.m)"
            } else {
                "<b>Area: Less than 1 guntaa</b> (${area.toInt()} sq.m)"
            }

            resultText.setText(android.text.Html.fromHtml(resultTextString, android.text.Html.FROM_HTML_MODE_LEGACY))
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
                    if (tracking) {
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
        return abs(area / 2.0) * 111320 * 111320 // approx square meters
    }

    data class LatLng(val latitude: Double, val longitude: Double)
}
