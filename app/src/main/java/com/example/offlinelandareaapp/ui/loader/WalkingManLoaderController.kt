package com.example.offlinelandareaapp.ui.loader

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Path
import android.view.View
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView

class WalkingManLoaderController(
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
        manIcon.playAnimation() // Start walking animation
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
