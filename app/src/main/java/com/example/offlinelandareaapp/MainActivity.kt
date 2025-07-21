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
import androidx.core.text.HtmlCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import com.airbnb.lottie.LottieAnimationView
import android.widget.Toast
import android.widget.FrameLayout


class MainActivity : AppCompatActivity() {

    data class LatLng(val latitude: Double, val longitude: Double)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val points = mutableListOf<LatLng>()
    private var tracking = false
    private var paused = false
    private lateinit var adView: AdView
    private lateinit var walkingLoader: WalkingManLoaderController
    private lateinit var walkingMsg: TextView

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
            walkingLoader.start()
            walkingMsg.text = "🚶 Now you start walking"
            walkingMsg.visibility = View.VISIBLE
            Toast.makeText(this, "Now you start walking", Toast.LENGTH_SHORT).show()
        }

        btnPause.setOnClickListener {
            walkingLoader.pause()
            walkingMsg.text = "⏸ Walking paused"
        }

        btnResume.setOnClickListener {
            walkingLoader.resume()
            walkingMsg.text = "🚶 Resumed walking"
        }

        btnStop.setOnClickListener {
            walkingLoader.stop()
            walkingMsg.visibility = View.GONE
        }

        startButton.setOnClickListener {
            points.clear()
            tracking = true
            paused = false
            startButton.visibility = View.GONE
            pauseButton.visibility = View.VISIBLE
            stopButton.visibility = View.VISIBLE
            resultText.text = "" // Hide previous result or "Press Start"
            startTracking()

            walkingMsg.visibility = View.VISIBLE
            Toast.makeText(this, "🚶 Now you start walking", Toast.LENGTH_SHORT).show()
        }

        pauseButton.setOnClickListener {
            paused = !paused
            pauseButton.text = if (paused) "Resume" else "Pause"
        }

        

        stopButton.setOnClickListener {
            walkingMsg.visibility = View.GONE
            tracking = false
            paused = false
            stopButton.visibility = View.GONE
            pauseButton.visibility = View.GONE
            startButton.visibility = View.VISIBLE

            val area = calculateArea(points) // area in sq.m

            val areaInAcre = area / 4046.86
            val gunthaExact = area / 101.17
            val gunthaRounded = gunthaExact.toInt()

            val displayText = if (areaInAcre < 1) {
                                // Less than 1 acre: show guntha only
                  if (gunthaRounded > 0) {
                    // Corrected to include parentheses around sq.m value
                    "<b><font color='black'>Area: $gunthaRounded Guntha</font></b> (<font color='black'>${area.toInt()} sq.m</font>)"
                } else {
                    // Corrected to include parentheses around sq.m value
                    "<b><font color='black'>Area: Less than 1 Guntha</font></b> (<font color='black'>${area.toInt()} sq.m</font>)"
                }
            } else {
                // Area >= 1 acre: show acres and leftover guntha
                val acresPart = areaInAcre.toInt()
                val leftoverSqm = area - (acresPart * 4046.86)
                val leftoverGuntha = (leftoverSqm / 101.17).toInt()

                val acreText = if (acresPart == 1) "1 acre" else "$acresPart acres"
                val gunthaText = if (leftoverGuntha > 0) " $leftoverGuntha Guntha" else ""

                "<b><font color='black'>Area: $acreText$gunthaText</font></b> (<font color='black'>${area.toInt()} sq.m</font>)"
            }

            resultText.text = HtmlCompat.fromHtml(displayText, HtmlCompat.FROM_HTML_MODE_LEGACY)
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
        return abs(area / 2.0) // area in square meters
    }

     /**
     * Simple controller to animate the Lottie view around a square loop overlay.
     */
    private class WalkingManLoaderController(
        private val root: FrameLayout,
        private val squareContainer: FrameLayout,
        private val manIcon: LottieAnimationView
    ) {
        private var animator: ObjectAnimator? = null
        private var isShowing = false

        /** Duration (ms) for one full lap. */
        var lapDurationMs: Long = 2400L

        fun start() {
            if (isShowing) return
            isShowing = true
            root.visibility = View.VISIBLE
            manIcon.playAnimation()
            // wait until laid out
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
