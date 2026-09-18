package com.example.mafiagame

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Brief themed loading screen shown once at app launch. Picks a random
 * villain-flavored punchline, fades it in, then hands off to MainActivity.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val punchlines = resources.getStringArray(R.array.loading_punchlines)
        val punchlineText = findViewById<TextView>(R.id.punchlineText)
        punchlineText.text = punchlines.random()
        punchlineText.animate().alpha(1f).setStartDelay(250).setDuration(500).start()

        punchlineText.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2200)
    }
}
