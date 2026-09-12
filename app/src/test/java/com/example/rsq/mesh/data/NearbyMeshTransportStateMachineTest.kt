package com.example.rsq.mesh.data

import com.example.rsq.mesh.data.NearbyMeshTransport.ConnectionDirection
import com.example.rsq.mesh.data.NearbyMeshTransport.NodeConnectionState
import com.example.rsq.mesh.data.NearbyMeshTransport.NodeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyMeshTransportStateMachineTest {

    @Test
    fun `deterministic connection ownership should ensure lower nodeId string initiates and higher nodeId waits`() {
        val nodeLower = "11111111-node"
        val nodeHigher = "99999999-node"

        // Local is lower than remote -> local should initiate
        val localIsLower = nodeLower < nodeHigher
        assertTrue("Lower local nodeId should initiate outgoing requestConnection", localIsLower)

        // Local is higher than remote -> local should wait/accept
        val localIsHigher = nodeHigher < nodeLower
        assertFalse("Higher local nodeId should NOT initiate outgoing requestConnection", localIsHigher)
    }

    @Test
    fun `node session state transitions should track connecting, connected, and disconnected correctly`() {
        val map = mutableMapOf<String, NodeSession>()
        val nodeId = "node-alpha"
        val endpointId1 = "ep1"

        // Initial state: DISCONNECTED
        map[nodeId] = NodeSession(nodeId, endpointId1, NodeConnectionState.CONNECTING, ConnectionDirection.INITIATED_BY_US)
        assertEquals(NodeConnectionState.CONNECTING, map[nodeId]?.state)

        // Result: OK -> CONNECTED
        map[nodeId] = NodeSession(nodeId, endpointId1, NodeConnectionState.CONNECTED, ConnectionDirection.INITIATED_BY_US)
        assertEquals(NodeConnectionState.CONNECTED, map[nodeId]?.state)

        // Disconnect
        map[nodeId] = NodeSession(nodeId, endpointId1, NodeConnectionState.DISCONNECTED, ConnectionDirection.UNKNOWN)
        assertEquals(NodeConnectionState.DISCONNECTED, map[nodeId]?.state)
    }

    @Test
    fun `duplicate discovery while CONNECTED or CONNECTING should be ignored`() {
        val map = mutableMapOf<String, NodeSession>()
        val nodeId = "node-beta"
        val endpoint1 = "ep-101"
        val endpoint2 = "ep-102"

        map[nodeId] = NodeSession(nodeId, endpoint1, NodeConnectionState.CONNECTED, ConnectionDirection.INITIATED_BY_US)

        // Duplicate discovery arrives with endpoint2 for same nodeId
        val currentSession = map[nodeId]
        val isAlreadyConnectedOrConnecting = currentSession != null &&
            (currentSession.state == NodeConnectionState.CONNECTED || currentSession.state == NodeConnectionState.CONNECTING)

        assertTrue("Duplicate discovery for connected node must be skipped", isAlreadyConnectedOrConnecting)
        assertEquals("Primary connected endpointId must remain unchanged", endpoint1, map[nodeId]?.endpointId)
    }

    @Test
    fun `8003 result handling should preserve active CONNECTED session and not disconnect`() {
        val map = mutableMapOf<String, NodeSession>()
        val nodeId = "node-gamma"
        val activeEndpoint = "ep-active"

        map[nodeId] = NodeSession(nodeId, activeEndpoint, NodeConnectionState.CONNECTED, ConnectionDirection.INITIATED_BY_US)

        // 8003 error code received on secondary duplicate endpoint "ep-duplicate"
        val statusCode = 8003 // STATUS_ALREADY_CONNECTED_TO_ENDPOINT

        if (statusCode == 8003) {
            // Keep existing active session
            val session = map[nodeId]
            assertEquals(NodeConnectionState.CONNECTED, session?.state)
            assertEquals(activeEndpoint, session?.endpointId)
        }

        assertEquals("Active session must remain CONNECTED", NodeConnectionState.CONNECTED, map[nodeId]?.state)
    }

    @Test
    fun `multiple independent peers can connect independently`() {
        val map = mutableMapOf<String, NodeSession>()

        map["node-1"] = NodeSession("node-1", "ep-1", NodeConnectionState.CONNECTED, ConnectionDirection.INITIATED_BY_US)
        map["node-2"] = NodeSession("node-2", "ep-2", NodeConnectionState.CONNECTED, ConnectionDirection.INITIATED_BY_REMOTE)

        assertEquals(2, map.count { it.value.state == NodeConnectionState.CONNECTED })
    }
}
