package com.grinmain.network

import com.grinmain.data.*
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    private fun baseUrl() = com.grinmain.data.Prefs.serverUrl

    private fun post(path: String, body: Map<String, Any>, token: String? = null): String {
        val json = gson.toJson(body)
        val req = Request.Builder()
            .url("${baseUrl()}$path")
            .post(json.toRequestBody(JSON))
            .apply { token?.let { header("Authorization", it) } }
            .build()
        return client.newCall(req).execute().use { it.body?.string() ?: "{}" }
    }

    private fun get(path: String, token: String): String {
        val req = Request.Builder()
            .url("${baseUrl()}$path")
            .get()
            .header("Authorization", token)
            .build()
        return client.newCall(req).execute().use { it.body?.string() ?: "{}" }
    }

    fun register(username: String, password: String): AuthResponse =
        gson.fromJson(post("/api/register", mapOf("username" to username, "password" to password)), AuthResponse::class.java)

    fun login(username: String, password: String): AuthResponse =
        gson.fromJson(post("/api/login", mapOf("username" to username, "password" to password)), AuthResponse::class.java)

    fun verifyToken(token: String): AuthResponse =
        gson.fromJson(post("/api/auth_token", mapOf("token" to token)), AuthResponse::class.java)

    fun getUsers(token: String): UsersResponse =
        gson.fromJson(get("/api/users", token), UsersResponse::class.java)

    fun getGroups(token: String): GroupsResponse =
        gson.fromJson(get("/api/groups", token), GroupsResponse::class.java)

    fun createGroup(token: String, name: String, members: List<String>): Group? {
        val resp = post("/api/groups", mapOf("name" to name, "members" to members), token)
        return try { gson.fromJson(resp, Group::class.java) } catch (e: Exception) { null }
    }

    fun getHistory(token: String, room: String): HistoryResponse =
        gson.fromJson(get("/api/history/$room", token), HistoryResponse::class.java)
}
