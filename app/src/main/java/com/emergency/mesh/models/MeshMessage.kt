package com.emergency.mesh.models

import java.io.Serializable

/**
 * Types of messages supported by the mesh network
 */
enum class MessageType {
    TEXT,
    VOICE,
    SOS
}

/**
 * Represents a message in the emergency mesh network
 */
data class MeshMessage(
    val id: String,
    val type: MessageType,
    val content: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val senderId: String,
    val hops: Int = 0,
    val audioData: ByteArray? = null
) : Serializable {

    /**
     * Get formatted location string
     */
    fun getLocationString(): String {
        return if (latitude != null && longitude != null) {
            "Lat: %.6f, Lon: %.6f".format(latitude, longitude)
        } else {
            "Location not available"
        }
    }

    /**
     * Create a copy with incremented hop count for relaying
     */
    fun relay(): MeshMessage {
        return copy(hops = hops + 1)
    }

    /**
     * Check if message has exceeded maximum hops
     */
    fun hasExceededMaxHops(): Boolean {
        return hops >= MAX_HOPS
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeshMessage

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        const val MAX_HOPS = 5 // Prevent infinite relay loops
    }
}
