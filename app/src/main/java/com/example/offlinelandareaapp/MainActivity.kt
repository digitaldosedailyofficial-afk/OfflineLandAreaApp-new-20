
package com.example.offlinelandareaapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private var isTracking = false
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        resultText = findViewById(R.id.resultText)

        startButton.setOnClickListener {
            isTracking = true
            startButton.visibility = Button.GONE
            stopButton.visibility = Button.VISIBLE
            resultText.text = "Tracking started..."
        }

        stopButton.setOnClickListener {
            isTracking = false
            stopButton.visibility = Button.GONE
            startButton.visibility = Button.VISIBLE
            resultText.text = "Calculated Area: 0.0 acres"
        }
    }
}
