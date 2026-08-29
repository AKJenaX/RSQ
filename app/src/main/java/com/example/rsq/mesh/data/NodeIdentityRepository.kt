package com.example.rsq.mesh.data

import android.content.Context
import android.content.SharedPreferences
import com.example.rsq.mesh.domain.NodeIdentityProvider
import java.util.UUID

/**
 * Implementation of [NodeIdentityProvider] that uses Android SharedPreferences
 * for persistent storage of the node ID.
 */
class NodeIdentityRepository(context: Context) : NodeIdentityProvider {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getNodeId(): String {
        var nodeId = prefs.getString(KEY_NODE_ID, null)

        if (nodeId == null) {
            nodeId = generateNewNodeId()
            persistNodeId(nodeId)
        }

        return nodeId
    }

    private fun generateNewNodeId(): String {
        return UUID.randomUUID().toString()
    }

    private fun persistNodeId(id: String) {
        prefs.edit().putString(KEY_NODE_ID, id).apply()
    }

    companion object {
        private const val PREFS_NAME = "mesh_identity_prefs"
        private const val KEY_NODE_ID = "local_node_id"
    }
}
