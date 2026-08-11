package com.example.rsq.mesh.domain

/**
 * Interface for providing the unique identifier of the local node in the mesh network.
 */
interface NodeIdentityProvider {
    /**
     * Returns the persistent unique identifier for this device.
     */
    fun getNodeId(): String
}
