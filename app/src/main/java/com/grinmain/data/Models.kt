package com.grinmain.data

import com.google.gson.annotations.SerializedName

data class User(val username: String, val online: Boolean = false)

data class Group(
    val id: String, val name: String,
    val members: List<String> = emptyList(),
    @SerializedName("created_by") val createdBy: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class Message(
    val id: String = "", val from: String,
    val text: String, val ts: String = "", val room: String = ""
)

data class CallInfo(
    val callId: String,
    val peer: String,
    val peers: List<String> = emptyList(),
    val type: CallType,
    var status: CallStatus,
    val isIncoming: Boolean,
    val isGroup: Boolean = false
)

enum class CallType { AUDIO, VIDEO }
enum class CallStatus { CALLING, RINGING, ACTIVE, ENDED, REJECTED }

data class AuthResponse(
    val success: Boolean = false, val token: String = "",
    val username: String = "", val error: String = ""
)
data class UsersResponse(val users: List<User> = emptyList())
data class GroupsResponse(val groups: List<Group> = emptyList())
data class HistoryResponse(val messages: List<Message> = emptyList())
