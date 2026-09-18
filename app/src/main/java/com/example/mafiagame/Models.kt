package com.example.mafiagame

enum class Role { DON, MUNNA_BHAI, CHULBUL_PANDEY, MAJDOOR }

enum class GamePhase {
    LOBBY, NIGHT_INTRO, MAFIA_TURN, DOCTOR_TURN, COP_TURN,
    RESOLVE, DISCUSSION, VOTE, VOTE_RESULT, GAME_OVER
}

data class Player(
    val endpointId: String,   // Nearby Connections endpoint id for this player's phone
    var name: String,
    var role: Role = Role.MAJDOOR,
    var isAlive: Boolean = true
)
