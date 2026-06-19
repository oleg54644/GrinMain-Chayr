package com.grinmain.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {
    private const val PREF_NAME = "grinmain_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, PREF_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    var username: String?
        get() = prefs.getString("username", null)
        set(v) = prefs.edit().putString("username", v).apply()

    // Server is locked after first login — wired into the app permanently
    var serverUrl: String
        get() = prefs.getString("server_url", "http://YOUR_SERVER_IP:5000") ?: "http://YOUR_SERVER_IP:5000"
        set(v) = prefs.edit().putString("server_url", v).apply()

    var serverLocked: Boolean
        get() = prefs.getBoolean("server_locked", false)
        set(v) = prefs.edit().putBoolean("server_locked", v).apply()

    var savedLogin: String?
        get() = prefs.getString("saved_login", null)
        set(v) = prefs.edit().putString("saved_login", v).apply()

    var savedPassword: String?
        get() = prefs.getString("saved_password", null)
        set(v) = prefs.edit().putString("saved_password", v).apply()

    fun clearSession() {
        prefs.edit().remove("token").remove("username").apply()
    }

    fun isLoggedIn() = !token.isNullOrEmpty() && !username.isNullOrEmpty()
}
