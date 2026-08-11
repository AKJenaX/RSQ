package com.example.rsq.mesh.model

/**
 * Defines the types of messages that can be transmitted through the offline mesh network.
 */
enum class MeshMessageType {
    /**
     * An urgent distress signal.
     */
    SOS,

    /**
     * A relay of a formal emergency report.
     */
    REPORT_RELAY,

    /**
     * A confirmation that a message was received.
     */
    ACKNOWLEDGEMENT
}
