package com.grinmain.ui

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grinmain.R
import com.grinmain.data.*
import com.grinmain.network.ApiClient
import com.grinmain.network.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnCallAudio: ImageButton
    private lateinit var btnCallVideo: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessagesAdapter

    private var room = ""
    private var title = ""
    private var isGroup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        room = intent.getStringExtra("room") ?: ""
        title = intent.getStringExtra("title") ?: ""
        isGroup = intent.getBooleanExtra("isGroup", false)

        rvMessages = findViewById(R.id.rvMessages)
        messageInput = findViewById(R.id.messageInput)
        btnSend = findViewById(R.id.btnSend)
        btnCallAudio = findViewById(R.id.btnCallAudio)
        btnCallVideo = findViewById(R.id.btnCallVideo)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)

        tvTitle.text = title
        if (isGroup) tvSubtitle.text = "Группа"
        else tvSubtitle.text = "Личный чат"

        // Hide call buttons for groups (group calls launched differently)
        if (isGroup) {
            btnCallAudio.setImageResource(R.drawable.ic_group_call)
            btnCallVideo.visibility = android.view.View.GONE
        }

        adapter = MessagesAdapter(messages, Prefs.username ?: "")
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = adapter

        btnSend.setOnClickListener { sendMessage() }
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        btnCallAudio.setOnClickListener { startCall("audio") }
        btnCallVideo.setOnClickListener { startCall("video") }

        // Listen for new messages
        SocketManager.onMessage.add { msg ->
            if (msg.room == room) {
                runOnUiThread {
                    messages.add(msg)
                    adapter.notifyItemInserted(messages.size - 1)
                    rvMessages.scrollToPosition(messages.size - 1)
                }
            }
        }

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val token = Prefs.token ?: return@launch
            val result = withContext(Dispatchers.IO) {
                try { ApiClient.getHistory(token, room) } catch (e: Exception) { null }
            }
            result?.messages?.let { list ->
                messages.clear()
                messages.addAll(list)
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty())
                    rvMessages.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return
        messageInput.setText("")
        SocketManager.sendMessage(room, text)
    }

    private fun startCall(type: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("peer", if (isGroup) room else title)
            putExtra("type", if (type == "video") "VIDEO" else "AUDIO")
            putExtra("isIncoming", false)
            putExtra("isGroup", isGroup)
            putExtra("room", room)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.onMessage.clear()
    }
}
