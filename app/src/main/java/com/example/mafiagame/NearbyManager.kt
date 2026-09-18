package com.example.mafiagame

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/**
 * Thin wrapper around Nearby Connections so the rest of the app never touches
 * the raw API. Handles both the host side (advertise + accept many players)
 * and the player side (discover host + connect).
 *
 * Strategy.P2P_STAR = one host, many clients, clients don't talk to each
 * other directly. Exactly the topology this game needs.
 */
class NearbyManager(context: Context) {

    private val serviceId = "com.example.mafiagame.SERVICE"
    private val strategy = Strategy.P2P_STAR
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

    // endpointId -> display name, only meaningful on the host
    val connectedEndpoints = mutableMapOf<String, String>()

    var onEndpointConnected: ((endpointId: String, name: String) -> Unit)? = null
    var onEndpointLost: ((endpointId: String) -> Unit)? = null
    var onPayloadReceived: ((endpointId: String, bytes: ByteArray) -> Unit)? = null

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept: fine for a trusted local party-game session.
            connectedEndpoints[endpointId] = info.endpointName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                onEndpointConnected?.invoke(endpointId, connectedEndpoints[endpointId] ?: "")
            } else {
                connectedEndpoints.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            onEndpointLost?.invoke(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { onPayloadReceived?.invoke(endpointId, it) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Not needed for small JSON payloads — they complete in one shot.
        }
    }

    // ---------------- HOST side ----------------

    fun startAdvertising(hostDisplayName: String) {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(
            hostDisplayName, serviceId, connectionLifecycleCallback, options
        )
    }

    fun stopAdvertising() = connectionsClient.stopAdvertising()

    // ---------------- PLAYER side ----------------

    fun startDiscovery(onHostFound: (endpointId: String, hostName: String) -> Unit) {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(serviceId, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                onHostFound(endpointId, info.endpointName)
            }
            override fun onEndpointLost(endpointId: String) {}
        }, options)
    }

    fun stopDiscovery() = connectionsClient.stopDiscovery()

    fun requestConnection(myDisplayName: String, hostEndpointId: String) {
        connectionsClient.requestConnection(myDisplayName, hostEndpointId, connectionLifecycleCallback)
    }

    // ---------------- shared ----------------

    fun send(endpointId: String, bytes: ByteArray) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    fun sendToAll(bytes: ByteArray) {
        connectedEndpoints.keys.forEach { send(it, bytes) }
    }

    fun stopAll() {
        connectionsClient.stopAllEndpoints()
    }
}
