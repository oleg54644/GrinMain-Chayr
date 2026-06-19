package com.grinmain.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.grinmain.R
import com.grinmain.data.Prefs
import com.grinmain.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthActivity : AppCompatActivity() {

    private lateinit var tabLogin: TextView
    private lateinit var tabRegister: TextView
    private lateinit var titleText: TextView
    private lateinit var serverInput: EditText
    private lateinit var serverGroup: LinearLayout
    private lateinit var loginInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var submitBtn: Button
    private lateinit var errorText: TextView
    private lateinit var progressBar: ProgressBar

    private var isRegister = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        tabLogin = findViewById(R.id.tabLogin)
        tabRegister = findViewById(R.id.tabRegister)
        titleText = findViewById(R.id.titleText)
        serverGroup = findViewById(R.id.serverGroup)
        serverInput = findViewById(R.id.serverInput)
        loginInput = findViewById(R.id.loginInput)
        passwordInput = findViewById(R.id.passwordInput)
        submitBtn = findViewById(R.id.submitBtn)
        errorText = findViewById(R.id.errorText)
        progressBar = findViewById(R.id.progressBar)

        // Pre-fill server if not locked
        serverInput.setText(Prefs.serverUrl)
        if (Prefs.serverLocked) {
            serverGroup.visibility = View.GONE
        }

        tabLogin.setOnClickListener { setTab(false) }
        tabRegister.setOnClickListener { setTab(true) }
        submitBtn.setOnClickListener { doAuth() }
        setTab(false)
    }

    private fun setTab(register: Boolean) {
        isRegister = register
        if (register) {
            tabRegister.alpha = 1f; tabLogin.alpha = 0.5f
            titleText.text = "Создать аккаунт"
            submitBtn.text = "Зарегистрироваться"
            serverGroup.visibility = if (Prefs.serverLocked) View.GONE else View.VISIBLE
        } else {
            tabLogin.alpha = 1f; tabRegister.alpha = 0.5f
            titleText.text = "Вход в GrinMain"
            submitBtn.text = "Войти"
            serverGroup.visibility = View.GONE
        }
        errorText.visibility = View.GONE
    }

    private fun doAuth() {
        val server = serverInput.text.toString().trim().ifEmpty { Prefs.serverUrl }
        val username = loginInput.text.toString().trim().lowercase()
        val password = passwordInput.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            showError("Заполните все поля"); return
        }

        if (isRegister) {
            if (server.isEmpty()) { showError("Укажите адрес сервера"); return }
            Prefs.serverUrl = server
        }

        setLoading(true)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    if (isRegister) ApiClient.register(username, password)
                    else ApiClient.login(username, password)
                } catch (e: Exception) {
                    null
                }
            }
            setLoading(false)
            if (result == null) {
                showError("Не удалось подключиться к серверу: $server")
                return@launch
            }
            if (!result.success || result.token.isEmpty()) {
                showError(result.error.ifEmpty { "Ошибка авторизации" })
                return@launch
            }
            // Save credentials and lock server
            Prefs.token = result.token
            Prefs.username = result.username
            Prefs.savedLogin = username
            Prefs.savedPassword = password
            Prefs.serverLocked = true

            startActivity(Intent(this@AuthActivity, MainActivity::class.java))
            finish()
        }
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        submitBtn.isEnabled = !loading
        loginInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
    }
}
