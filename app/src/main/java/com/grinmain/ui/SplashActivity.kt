package com.grinmain.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.grinmain.data.Prefs
import com.grinmain.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            delay(600) // brief splash

            val token = Prefs.token
            if (token != null) {
                // Verify saved token
                val result = withContext(Dispatchers.IO) {
                    try { ApiClient.verifyToken(token) }
                    catch (e: Exception) { null }
                }
                if (result?.success == true) {
                    Prefs.username = result.username
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    // Token invalid — try auto-login with saved credentials
                    val login = Prefs.savedLogin
                    val pass = Prefs.savedPassword
                    if (login != null && pass != null) {
                        val loginResult = withContext(Dispatchers.IO) {
                            try { ApiClient.login(login, pass) }
                            catch (e: Exception) { null }
                        }
                        if (loginResult?.success == true) {
                            Prefs.token = loginResult.token
                            Prefs.username = loginResult.username
                            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        } else {
                            startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
                        }
                    } else {
                        startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
                    }
                }
            } else {
                startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
            }
            finish()
        }
    }
}
