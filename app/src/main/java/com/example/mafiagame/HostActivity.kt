package com.example.mafiagame

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

class HostActivity : AppCompatActivity() {

    private lateinit var nearby: NearbyManager
    private lateinit var tts: TextToSpeech
    private lateinit var lobbyText: TextView
    private lateinit var hostTimerText: TextView
    private lateinit var hostFlavorText: TextView

    private val players = mutableListOf<Player>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var discussionSeconds = 180
    private var gameStarted = false

    // Set while we are waiting on exactly one specific role's tap.
    private var waitingForRole: Role? = null
    private var pendingSingleResponse: CompletableDeferred<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)
        lobbyText = findViewById(R.id.lobbyListText)
        hostTimerText = findViewById(R.id.hostTimerText)
        hostFlavorText = findViewById(R.id.hostFlavorText)
        startLobbyFlavorRotation()

        tts = TextToSpeech(this) { status ->
            Log.d("MafiaTTS", "onInit status=$status (SUCCESS=${TextToSpeech.SUCCESS})")
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, "No text-to-speech engine available on this device.", Toast.LENGTH_LONG).show()
                return@TextToSpeech
            }

            var langResult = tts.setLanguage(Locale.getDefault())
            Log.d("MafiaTTS", "setLanguage(${Locale.getDefault()}) result=$langResult")
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Device's system locale has no downloaded voice data for it (common
                // outside en-US) — fall back to a locale that ships by default rather
                // than silently failing every speak() call afterward.
                langResult = tts.setLanguage(Locale.US)
                Log.d("MafiaTTS", "fallback setLanguage(en_US) result=$langResult")
            }
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(
                    this,
                    "No text-to-speech voice data installed. Install one under system Settings > Text-to-speech.",
                    Toast.LENGTH_LONG
                ).show()
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { Log.w("MafiaTTS", "utterance failed: $utteranceId") }
            })
        }

        nearby = NearbyManager(this)
        nearby.onEndpointConnected = { endpointId, name ->
            if (players.none { it.endpointId == endpointId }) {
                players.add(Player(endpointId, name))
            }
            refreshLobbyUi()
        }
        nearby.onEndpointLost = { endpointId ->
            players.removeAll { it.endpointId == endpointId }
            refreshLobbyUi()
        }
        nearby.onPayloadReceived = { endpointId, bytes -> handleIncoming(endpointId, bytes) }
        nearby.startAdvertising("Host")

        findViewById<Button>(R.id.startGameButton).setOnClickListener {
            if (players.size >= 5) {
                val minutes = findViewById<EditText>(R.id.discussionMinutesInput).text.toString().toIntOrNull()
                discussionSeconds = if (minutes != null && minutes > 0) minutes * 60 else 180
                findViewById<Button>(R.id.startGameButton).isEnabled = false
                gameStarted = true
                hostFlavorText.text = ""
                startGame()
            } else {
                Toast.makeText(this, "Need at least 5 players to start", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshLobbyUi() {
        lobbyText.text = "👥 Connected (${players.size}):\n\n" +
            players.joinToString("\n") { "  •  ${it.name}" }
    }

    /** Cycles a random villain-flavored line under the title while the lobby fills up. */
    private fun startLobbyFlavorRotation() {
        val lines = resources.getStringArray(R.array.lobby_punchlines)
        scope.launch {
            while (!gameStarted) {
                hostFlavorText.text = lines.random()
                delay(3500)
            }
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    // Default handler used outside the special vote-collection window below.
    private fun handleIncoming(endpointId: String, bytes: ByteArray) {
        val json = Protocol.parse(bytes)
        if (json.getString("type") != "ACTION_RESPONSE") return
        val actor = players.find { it.endpointId == endpointId } ?: return
        if (actor.role == waitingForRole) {
            pendingSingleResponse?.complete(json.getString("target"))
        }
    }

    private fun startGame() {
        val shuffled = players.shuffled().toMutableList()
        shuffled[0].role = Role.DON
        shuffled[1].role = Role.MUNNA_BHAI
        shuffled[2].role = Role.CHULBUL_PANDEY
        for (i in 3 until shuffled.size) shuffled[i].role = Role.MAJDOOR

        players.forEach { nearby.send(it.endpointId, Protocol.roleAssign(it.role)) }
        scope.launch { gameLoop() }
    }

    private suspend fun gameLoop() {
        while (true) {
            nightPhase()

            val don = players.find { it.role == Role.DON }
            if (don == null || !don.isAlive) { announceWinner("Villagers"); return }

            val alive = players.filter { it.isAlive }
            val mafiaCount = alive.count { it.role == Role.DON }
            if (mafiaCount >= alive.size - mafiaCount) { announceWinner("Mafia"); return }

            val eliminated = dayPhase()
            if (eliminated?.role == Role.DON) { announceWinner("Villagers"); return }

            val aliveAfterVote = players.filter { it.isAlive }
            val mafiaAfterVote = aliveAfterVote.count { it.role == Role.DON }
            if (mafiaAfterVote == 0) { announceWinner("Villagers"); return }
            if (mafiaAfterVote >= aliveAfterVote.size - mafiaAfterVote) { announceWinner("Mafia"); return }
        }
    }

    private suspend fun nightPhase() {
        speak("It's night in the town. Everyone close their eyes.")
        nearby.sendToAll(Protocol.phaseUpdate("Everyone is asleep..."))
        delay(3000)

        val killTarget = askRole(Role.DON, "Mafia, wake up and choose someone to kill.")
        delay(5000)
        val saveTarget = askRole(Role.MUNNA_BHAI, "Doctor, wake up and choose someone to save.")
        delay(5000)
        val copGuess = askRole(Role.CHULBUL_PANDEY, "Cop, wake up and choose someone you suspect.")
        delay(5000)

        var killedName: String? = null
        if (killTarget != null && killTarget != saveTarget) {
            players.find { it.name == killTarget && it.isAlive }?.let {
                it.isAlive = false
                killedName = it.name
            }
        }

        val don = players.find { it.role == Role.DON }
        val copCorrect: Boolean? = if (copGuess != null && don != null) copGuess == don.name else null

        speak(if (killedName != null) "$killedName was killed last night." else "Nobody was killed last night.")
        if (copCorrect != null) {
            speak(if (copCorrect) "The cop's guess was correct." else "The cop's guess was wrong.")
        }

        nearby.sendToAll(Protocol.resultUpdate(killedName, copCorrect))
    }

    /**
     * Sends an ACTION_REQUEST to the single alive player holding [role] and
     * waits up to 15 seconds for their tap. Returns null if that role is
     * dead, already out of the game, or doesn't respond in time.
     */
    private suspend fun askRole(role: Role, prompt: String): String? {
        speak(prompt)
        val actor = players.find { it.role == role && it.isAlive } ?: return null

        val choices = players.filter { it.isAlive }.map { it.name }
        waitingForRole = role
        pendingSingleResponse = CompletableDeferred()
        nearby.send(actor.endpointId, Protocol.actionRequest(role.name, choices))

        val result = withTimeoutOrNull(15_000) { pendingSingleResponse!!.await() }
        waitingForRole = null
        pendingSingleResponse = null
        return result
    }

    /** Discussion timer, then a vote among everyone still alive. */
    private suspend fun dayPhase(): Player? {
        speak("Discussion time. You have ${spokenTime(discussionSeconds)}.")
        nearby.sendToAll(Protocol.timerStart(discussionSeconds))
        hostTimerText.visibility = android.view.View.VISIBLE
        runCountdown(discussionSeconds)
        hostTimerText.visibility = android.view.View.GONE

        speak("Voting time. Choose who to eliminate, or skip.")
        val alive = players.filter { it.isAlive }
        val choices = alive.map { it.name } + "SKIP"

        val votes = mutableListOf<String>()
        val allVotesIn = CompletableDeferred<Unit>()
        val originalHandler = nearby.onPayloadReceived
        nearby.onPayloadReceived = { _, bytes ->
            val json = Protocol.parse(bytes)
            if (json.getString("type") == "ACTION_RESPONSE") {
                votes.add(json.getString("target"))
                if (votes.size >= alive.size) allVotesIn.complete(Unit)
            }
        }

        alive.forEach { nearby.send(it.endpointId, Protocol.actionRequest("VOTE", choices)) }
        withTimeoutOrNull(30_000) { allVotesIn.await() }
        nearby.onPayloadReceived = originalHandler

        val tally = votes.filter { it != "SKIP" }.groupingBy { it }.eachCount()
        val top = tally.maxByOrNull { it.value }
        val isClearWinner = top != null && tally.values.count { it == top.value } == 1
        val eliminated = if (isClearWinner) players.find { it.name == top!!.key } else null
        eliminated?.isAlive = false

        speak(eliminated?.let { "${it.name} was voted out." } ?: "No one was eliminated.")
        nearby.sendToAll(Protocol.voteResult(eliminated?.name))
        return eliminated
    }

    /** Ticks [hostTimerText] down from [totalSeconds] to 0, once per second. */
    private suspend fun runCountdown(totalSeconds: Int) {
        for (remaining in totalSeconds downTo 0) {
            hostTimerText.text = formatTime(remaining)
            if (remaining > 0) delay(1000)
        }
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    private fun spokenTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        val minutesPart = if (m > 0) "$m minute${if (m != 1) "s" else ""}" else null
        val secondsPart = if (s > 0) "$s second${if (s != 1) "s" else ""}" else null
        return listOfNotNull(minutesPart, secondsPart).joinToString(" and ").ifEmpty { "0 seconds" }
    }

    private fun announceWinner(winner: String) {
        speak("$winner win the game!")
        nearby.sendToAll(Protocol.gameOver(winner))
    }

    override fun onDestroy() {
        super.onDestroy()
        nearby.stopAll()
        tts.shutdown()
    }
}
