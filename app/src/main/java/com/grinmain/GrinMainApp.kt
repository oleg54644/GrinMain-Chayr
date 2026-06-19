package com.grinmain

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.grinmain.data.Prefs

class GrinMainApp : Application() {
    companion object {
        lateinit var instance: GrinMainApp private set
        const val CHANNEL_CALLS = "grinmain_calls"
        const val CHANNEL_MSGS  = "grinmain_messages"
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        createNotificationChannels()
    }
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_CALLS, "Звонки", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_MSGS, "Сообщения", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
