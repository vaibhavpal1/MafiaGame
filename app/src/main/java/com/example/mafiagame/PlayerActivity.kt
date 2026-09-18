package com.example.mafiagame

import android.os.Bundle
import android.os.CountDownTimer
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class PlayerActivity : AppCompatActivity() {

    private lateinit var nearby: NearbyManager
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var choicesLayout: LinearLayout
    private lateinit var roleBadge: TextView
    private lateinit var roleRevealOverlay: FrameLayout
    private lateinit var roleCardPanel: LinearLayout
    private lateinit var roleEmoji: TextView
    private lateinit var roleTitleText: TextView
    private lateinit var roleSubtitleText: TextView
    private lateinit var roleTaglineText: TextView
    private lateinit var hostEndpointId: String
    private var myRole: String = "MAJDOOR"
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        statusText = findViewById(R.id.statusText)
        timerText = findViewById(R.id.timerText)
        choicesLayout = findViewById(R.id.choicesLayout)
        roleBadge = findViewById(R.id.roleBadge)
        roleRevealOverlay = findViewById(R.id.roleRevealOverlay)
        roleCardPanel = findViewById(R.id.roleCardPanel)
        roleEmoji = findViewById(R.id.roleEmoji)
        roleTitleText = findViewById(R.id.roleTitleText)
        roleSubtitleText = findViewById(R.id.roleSubtitleText)
        roleTaglineText = findViewById(R.id.roleTaglineText)

        findViewById<Button>(R.id.enterTownButton).setOnClickListener {
            dismissRoleReveal()
        }

        val myName = intent.getStringExtra("PLAYER_NAME") ?: "Player"
        hostEndpointId = intent.getStringExtra("HOST_ENDPOINT_ID")!!

        statusText.text = "Connecting to host..."
        nearby = NearbyManager(this)
        nearby.onPayloadReceived = { _, bytes -> handleMessage(bytes) }
        nearby.onEndpointConnected = { _, _ ->
            runOnUiThread { statusText.text = "Connected. Waiting for host to start the game..." }
        }
        nearby.requestConnection(myName, hostEndpointId)
    }

    private fun handleMessage(bytes: ByteArray) {
        val json = Protocol.parse(bytes)
        runOnUiThread {
            when (json.getString("type")) {
                "ROLE_ASSIGN" -> {
                    myRole = json.getString("role")
                    showRoleReveal(myRole)
                }
                "PHASE_UPDATE" -> {
                    stopCountdown()
                    statusText.text = json.getString("message")
                    choicesLayout.removeAllViews()
                }
                "ACTION_REQUEST" -> {
                    stopCountdown()
                    showActionRequest(json)
                }
                "RESULT_UPDATE" -> {
                    stopCountdown()
                    val killed = if (json.isNull("killed")) null else json.getString("killed")
                    statusText.text = if (killed != null) "$killed was killed last night." else "Nobody was killed last night."
                    choicesLayout.removeAllViews()
                }
                "TIMER_START" -> {
                    statusText.text = "Discussion time"
                    choicesLayout.removeAllViews()
                    startCountdown(json.getInt("seconds"))
                }
                "VOTE_RESULT" -> {
                    stopCountdown()
                    val eliminated = if (json.isNull("eliminated")) null else json.getString("eliminated")
                    statusText.text = eliminated?.let { "$it was voted out." } ?: "No one was eliminated."
                    choicesLayout.removeAllViews()
                }
                "GAME_OVER" -> {
                    stopCountdown()
                    statusText.text = "${json.getString("winner")} win the game!"
                    choicesLayout.removeAllViews()
                }
            }
        }
    }

    private fun startCountdown(totalSeconds: Int) {
        stopCountdown()
        timerText.visibility = android.view.View.VISIBLE
        timerText.text = formatTime(totalSeconds)
        countdownTimer = object : CountDownTimer(totalSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = ((millisUntilFinished + 999) / 1000).toInt()
                timerText.text = formatTime(secondsLeft)
            }
            override fun onFinish() {
                timerText.text = formatTime(0)
            }
        }.also { it.start() }
    }

    private fun stopCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        timerText.text = ""
        timerText.visibility = android.view.View.GONE
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    private fun showActionRequest(json: org.json.JSONObject) {
        val actionFor = json.getString("actionFor")
        val choicesArray = json.getJSONArray("choices")
        choicesLayout.removeAllViews()

        // Only the matching role (or everyone, during a vote) gets buttons.
        // Everyone else just sees a waiting message — this is how their
        // "eyes stay closed" during another role's turn.
        if (actionFor != myRole && actionFor != "VOTE") {
            statusText.text = "Everyone is asleep..."
            return
        }

        statusText.text = actionPrompt(actionFor)
        for (i in 0 until choicesArray.length()) {
            val name = choicesArray.getString(i)
            val button = Button(this).apply {
                text = name
                setTextColor(getColor(R.color.text_primary))
                background = getDrawable(R.drawable.bg_choice_button_selector)
                val padH = (16 * resources.displayMetrics.density).toInt()
                val padV = (12 * resources.displayMetrics.density).toInt()
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    nearby.send(hostEndpointId, Protocol.actionResponse(name))
                    choicesLayout.removeAllViews()
                    statusText.text = randomWaitingPunchline()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (8 * resources.displayMetrics.density).toInt()
            button.layoutParams = params
            choicesLayout.addView(button)
        }
    }

    private fun randomWaitingPunchline(): String =
        resources.getStringArray(R.array.waiting_punchlines).random()

    /** Populates and reveals the themed role card handed to the player. */
    private fun showRoleReveal(roleName: String) {
        val info = RoleUi.forRoleName(roleName)
        roleEmoji.text = info.emoji
        roleTitleText.text = info.title
        roleTitleText.setTextColor(getColor(info.accentColorRes))
        roleSubtitleText.text = info.subtitle
        roleTaglineText.text = info.tagline
        roleCardPanel.setBackgroundResource(info.cardBackgroundRes)

        roleBadge.text = "${info.emoji}  ${info.title}"
        roleBadge.setTextColor(getColor(info.accentColorRes))
        roleBadge.visibility = android.view.View.VISIBLE

        roleRevealOverlay.alpha = 0f
        roleRevealOverlay.visibility = android.view.View.VISIBLE
        roleCardPanel.scaleX = 0.8f
        roleCardPanel.scaleY = 0.8f
        roleRevealOverlay.animate().alpha(1f).setDuration(250).start()
        roleCardPanel.animate()
            .scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator())
            .setDuration(400)
            .start()
    }

    private fun dismissRoleReveal() {
        roleRevealOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            roleRevealOverlay.visibility = android.view.View.GONE
        }.start()
        statusText.text = "Waiting for the host to begin the night..."
    }

    private fun actionPrompt(actionFor: String) = when (actionFor) {
        "DON" -> "Choose someone to kill"
        "MUNNA_BHAI" -> "Choose someone to save"
        "CHULBUL_PANDEY" -> "Choose someone you suspect"
        "VOTE" -> "Vote to eliminate, or tap SKIP"
        else -> ""
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
        nearby.stopAll()
    }
}
