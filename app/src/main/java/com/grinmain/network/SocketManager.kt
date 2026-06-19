package com.grinmain.network

import android.util.Log
import com.grinmain.data.*
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.json.JSONArray
import java.net.URI

typealias MsgCallback = (Message) -> Unit
typealias CallCallback = (CallInfo) -> Unit
typealias UserCallback = (String, Boolean) -> Unit

object SocketManager {
    private val TAG = "SocketManager"
    private val gson = Gson()
    private var socket: Socket? = null

    // Observers
    val onMessage = mutableListOf<MsgCallback>()
    val onIncomingCall = mutableListOf<CallCallback>()
    val onCallAccepted = mutableListOf<(String) -> Unit>()
    val onCallRejected = mutableListOf<(String) -> Unit>()
    val onCallEnded = mutableListOf<(String) -> Unit>()
    val onUserStatus = mutableListOf<UserCallback>()
    val onOnlineUsers = mutableListOf<(List<String>) -> Unit>()
    val onGroupCreated = mutableListOf<(Group) -> Unit>()

    // WebRTC signal callbacks
    val onWebRtcOffer = mutableListOf<(from: String, sdp: String, callId: String) -> Unit>()
    val onWebRtcAnswer = mutableListOf<(from: String, sdp: String, callId: String) -> Unit>()
    val onWebRtcIce = mutableListOf<(from: String, candidate: String, callId: String) -> Unit>()

    fun connect(serverUrl: String, token: String) {
        try {
            val opts = IO.Options.builder()
                .setReconnection(true)
                .setReconnectionAttempts(Int.MAX_VALUE)
                .setReconnectionDelay(1000)
                .build()
            socket = IO.socket(URI.create(serverUrl), opts)
            setupListeners(token)
            socket!!.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Connect error: ${e.message}")
        }
    }

    private fun setupListeners(token: String) {
        val s = socket ?: return

        s.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "Connected, authenticating")
            s.emit("authenticate", JSONObject().put("token", token))
        }

        s.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "Disconnected — will reconnect")
        }

        s.on("new_message") { args ->
            try {
                val obj = args[0] as JSONObject
                val msg = Message(
                    id = obj.optString("id"),
                    from = obj.getString("from"),
                    text = obj.getString("text"),
                    ts = obj.optString("ts"),
                    room = obj.getString("room")
                )
                onMessage.forEach { it(msg) }
            } catch (e: Exception) { Log.e(TAG, "new_message: ${e.message}") }
        }

        s.on("online_users") { args ->
            try {
                val obj = args[0] as JSONObject
                val arr = obj.getJSONArray("users")
                val list = (0 until arr.length()).map { arr.getString(it) }
                onOnlineUsers.forEach { it(list) }
            } catch (e: Exception) { Log.e(TAG, "online_users: ${e.message}") }
        }

        s.on("user_online") { args ->
            val obj = args[0] as JSONObject
            onUserStatus.forEach { it(obj.getString("username"), true) }
        }

        s.on("user_offline") { args ->
            val obj = args[0] as JSONObject
            onUserStatus.forEach { it(obj.getString("username"), false) }
        }

        s.on("group_created") { args ->
            try {
                val obj = args[0] as JSONObject
                val members = mutableListOf<String>()
                val arr = obj.optJSONArray("members")
                if (arr != null) for (i in 0 until arr.length()) members.add(arr.getString(i))
                val group = Group(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    members = members,
                    createdBy = obj.optString("created_by")
                )
                onGroupCreated.forEach { it(group) }
            } catch (e: Exception) { Log.e(TAG, "group_created: ${e.message}") }
        }

        // ── Call signaling ──────────────────────────────────────────────────

        s.on("incoming_call") { args ->
            try {
                val obj = args[0] as JSONObject
                val callId = obj.getString("call_id")
                val caller = obj.getString("caller")
                val typeStr = obj.optString("type", "audio")
                val isGroup = obj.optBoolean("is_group", false)
                val peersArr = obj.optJSONArray("peers")
                val peers = if (peersArr != null) (0 until peersArr.length()).map { peersArr.getString(it) } else emptyList()

                val call = CallInfo(
                    callId = callId,
                    peer = caller,
                    peers = peers,
                    type = if (typeStr == "video") CallType.VIDEO else CallType.AUDIO,
                    status = CallStatus.RINGING,
                    isIncoming = true,
                    isGroup = isGroup
                )
                onIncomingCall.forEach { it(call) }
            } catch (e: Exception) { Log.e(TAG, "incoming_call: ${e.message}") }
        }

        s.on("call_accepted") { args ->
            val obj = args[0] as JSONObject
            onCallAccepted.forEach { it(obj.getString("call_id")) }
        }

        s.on("call_rejected") { args ->
            val obj = args[0] as JSONObject
            onCallRejected.forEach { it(obj.getString("call_id")) }
        }

        s.on("call_ended") { args ->
            val obj = args[0] as JSONObject
            onCallEnded.forEach { it(obj.getString("call_id")) }
        }

        // ── WebRTC ──────────────────────────────────────────────────────────

        s.on("webrtc_offer") { args ->
            val obj = args[0] as JSONObject
            onWebRtcOffer.forEach { it(obj.getString("from"), obj.getString("sdp"), obj.optString("call_id")) }
        }

        s.on("webrtc_answer") { args ->
            val obj = args[0] as JSONObject
            onWebRtcAnswer.forEach { it(obj.getString("from"), obj.getString("sdp"), obj.optString("call_id")) }
        }

        s.on("webrtc_ice") { args ->
            val obj = args[0] as JSONObject
            onWebRtcIce.forEach { it(obj.getString("from"), obj.optString("candidate",""), obj.optString("call_id")) }
        }
    }

    fun sendMessage(room: String, text: String) {
        socket?.emit("send_message", JSONObject().put("room", room).put("text", text))
    }

    fun requestCall(callee: String, type: String, isGroup: Boolean = false, peers: List<String> = emptyList()) {
        val obj = JSONObject()
            .put("callee", callee)
            .put("type", type)
            .put("is_group", isGroup)
        if (peers.isNotEmpty()) {
            val arr = JSONArray()
            peers.forEach { arr.put(it) }
            obj.put("peers", arr)
        }
        socket?.emit("call_request", obj)
    }

    fun answerCall(callId: String, accepted: Boolean) {
        socket?.emit("call_answer", JSONObject().put("call_id", callId).put("accepted", accepted))
    }

    fun endCall(callId: String) {
        socket?.emit("call_end", JSONObject().put("call_id", callId))
    }

    fun sendOffer(target: String, sdp: String, callId: String) {
        socket?.emit("webrtc_offer", JSONObject().put("target", target).put("sdp", sdp).put("call_id", callId))
    }

    fun sendAnswer(target: String, sdp: String, callId: String) {
        socket?.emit("webrtc_answer", JSONObject().put("target", target).put("sdp", sdp).put("call_id", callId))
    }

    fun sendIceCandidate(target: String, candidate: String, callId: String) {
        socket?.emit("webrtc_ice", JSONObject().put("target", target).put("candidate", candidate).put("call_id", callId))
    }

    fun isConnected() = socket?.connected() == true

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}
