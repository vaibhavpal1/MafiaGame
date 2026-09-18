package com.example.mafiagame

import org.json.JSONArray
import org.json.JSONObject

/**
 * All messages between host and player phones are small JSON objects sent as
 * raw bytes over a Nearby Connections payload. Every message has a "type"
 * field so the receiver knows how to react to it.
 */
object Protocol {

    fun roleAssign(role: Role): ByteArray =
        JSONObject().put("type", "ROLE_ASSIGN").put("role", role.name)
            .toString().toByteArray()

    // Sent once, on night 1, privately to the Don and the Right Hand so they
    // recognize each other. Every field is pre-formatted by the host so
    // PlayerActivity doesn't need to know who's who.
    fun allyReveal(title: String, subtitle: String, tagline: String): ByteArray =
        JSONObject().put("type", "ALLY_REVEAL")
            .put("title", title)
            .put("subtitle", subtitle)
            .put("tagline", tagline)
            .toString().toByteArray()

    fun phaseUpdate(message: String): ByteArray =
        JSONObject().put("type", "PHASE_UPDATE").put("message", message)
            .toString().toByteArray()

    // actionFor: "DON" | "MUNNA_BHAI" | "CHULBUL_PANDEY" | "VOTE"
    fun actionRequest(actionFor: String, choices: List<String>): ByteArray =
        JSONObject().put("type", "ACTION_REQUEST")
            .put("actionFor", actionFor)
            .put("choices", JSONArray(choices))
            .toString().toByteArray()

    fun actionResponse(targetName: String): ByteArray =
        JSONObject().put("type", "ACTION_RESPONSE").put("target", targetName)
            .toString().toByteArray()

    fun timerStart(seconds: Int): ByteArray =
        JSONObject().put("type", "TIMER_START").put("seconds", seconds)
            .toString().toByteArray()

    fun resultUpdate(killedName: String?, copCorrect: Boolean?): ByteArray {
        val obj = JSONObject().put("type", "RESULT_UPDATE")
        obj.put("killed", killedName ?: JSONObject.NULL)
        obj.put("copCorrect", copCorrect ?: JSONObject.NULL)
        return obj.toString().toByteArray()
    }

    fun voteResult(eliminatedName: String?): ByteArray =
        JSONObject().put("type", "VOTE_RESULT")
            .put("eliminated", eliminatedName ?: JSONObject.NULL)
            .toString().toByteArray()

    fun gameOver(winner: String): ByteArray =
        JSONObject().put("type", "GAME_OVER").put("winner", winner)
            .toString().toByteArray()

    fun parse(bytes: ByteArray): JSONObject = JSONObject(String(bytes))
}
