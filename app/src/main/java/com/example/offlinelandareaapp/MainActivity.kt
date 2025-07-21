package com.example.offlinelandareaapp

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.text.HtmlCompat
import com.airbnb.lottie.LottieAnimationView
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

    // Loader and message
    private lateinit var walkingLoader: WalkingManLoaderController
    private lateinit var walkingMsg: TextView

    // New buttons
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnResume: Button
    private lateinit var btnStop: Button

    private lateinit var loaderRoot: FrameLayout
    private lateinit var squareContainer: FrameLayout
    private lateinit var manIcon: LottieAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // --- findViews ---
        walkingMsg = findViewById(R.id.walkingMessage)
        btnStart = findViewById(R.id.btnStart)
        btnPause = findViewById(R.id.btnPause)
        btnResume = findViewById(R.id.btnResume)
        btnStop = findViewById(R.id.btnStop)

        loaderRoot = findViewById(R.id.walkingManLoaderRoot)
        squareContainer = findViewById(R.id.walkingSquareContainer)
        manIcon = findViewById(R.id.walkingManIcon)

        // --- init loader ---
        walkingLoader = WalkingManLoaderController(
            root = loaderRoot,
            squareContainer = squareContainer,
            manIcon = manIcon
        ).apply {
            lapDurationMs = 3000L
        }

        // --- button wiring ---
        btnStart.setOnClickListener {
            points.clear()
            tracking = true
            paused = false
            walkingLoader.start()
            walkingMsg.text = "🚶 Now you start walking"
            walkingMsg.visibility = View.VISIBLE
            Toast.makeText(this, "Now you start walking", Toast.LENGTH_SHORT).show()
            startTracking()
        }

        btnPause.setOnClickListener {
            walkingLoader.pause()
            paused = true
            walkingMsg.text = "⏸ Walking paused"
        }

        btnResume.setOnClickListener {
            walkingLoader.resume()
            paused = false
            walkingMsg.text = "🚶 Resumed walking"
        }

        btnStop.setOnClickListener {
            walkingLoader.stop()
            walkingMsg.visibility = View.GONE
            tracking = false
            paused = false
            calculateAndShowArea()
        }

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Load the banner ad
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
        val area = calculateArea(points) // area in sq.m
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

    private class WalkingManLoaderController(
        private val root: FrameLayout,
        private val squareContainer: FrameLayout,
        private val manIcon: LottieAnimationView
    ) {
        private var animator: ObjectAnimator? = null
        private var isShowing = false
        var lapDurationMs: Long = 2400L

        fun start() {
            if (isShowing) return
            isShowing = true
            root.visibility = View.VISIBLE
            manIcon.playAnimation()
            root.post { startAnimationInternal() }
        }

        fun pause() {
            animator?.pause()
            manIcon.pauseAnimation()
        }

        fun resume() {
            animator?.resume()
            manIcon.resumeAnimation()
        }

        fun stop() {
            animator?.cancel()
            animator = null
            manIcon.cancelAnimation()
            root.visibility = View.GONE
            isShowing = false
        }

        private fun startAnimationInternal() {
            val w = squareContainer.width.toFloat()
            val h = squareContainer.height.toFloat()
            if (w == 0f || h == 0f) return

            manIcon.translationX = 0f
            manIcon.translationY = 0f

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, h)
                lineTo(0f, h)
                lineTo(0f, 0f)
            }

            animator = ObjectAnimator.ofFloat(manIcon, View.X, View.Y, path).apply {
                duration = lapDurationMs
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                start()
            }
        }
    }
}
