package com.grinmain.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
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

class MainActivity : AppCompatActivity() {

    private lateinit var rvContacts: RecyclerView
    private lateinit var rvGroups: RecyclerView
    private lateinit var tabChats: TextView
    private lateinit var tabGroups: TextView
    private lateinit var btnNewGroup: ImageButton
    private lateinit var tvUsername: TextView
    private lateinit var tvStatus: TextView

    private val contacts = mutableListOf<User>()
    private val groups = mutableListOf<Group>()
    private val onlineUsers = mutableSetOf<String>()

    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var groupsAdapter: GroupsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvContacts = findViewById(R.id.rvContacts)
        rvGroups = findViewById(R.id.rvGroups)
        tabChats = findViewById(R.id.tabChats)
        tabGroups = findViewById(R.id.tabGroups)
        btnNewGroup = findViewById(R.id.btnNewGroup)
        tvUsername = findViewById(R.id.tvUsername)
        tvStatus = findViewById(R.id.tvStatus)

        tvUsername.text = Prefs.username ?: ""

        contactsAdapter = ContactsAdapter(contacts, onlineUsers) { user ->
            openChat(user.username, "dm_${listOf(Prefs.username!!, user.username).sorted().joinToString("_")}", false)
        }
        groupsAdapter = GroupsAdapter(groups) { group ->
            openChat(group.name, group.id, true)
        }

        rvContacts.layoutManager = LinearLayoutManager(this)
        rvContacts.adapter = contactsAdapter
        rvGroups.layoutManager = LinearLayoutManager(this)
        rvGroups.adapter = groupsAdapter

        tabChats.setOnClickListener { showTab(false) }
        tabGroups.setOnClickListener { showTab(true) }
        btnNewGroup.setOnClickListener { showCreateGroupDialog() }
        showTab(false)

        // Connect socket
        connectSocket()

        // Load data
        loadData()

        // Handle incoming calls
        SocketManager.onIncomingCall.add { call ->
            runOnUiThread { launchCallActivity(call) }
        }
    }

    private fun connectSocket() {
        if (!SocketManager.isConnected()) {
            SocketManager.connect(Prefs.serverUrl, Prefs.token ?: "")
        }
        SocketManager.onUserStatus.add { username, isOnline ->
            runOnUiThread {
                if (isOnline) onlineUsers.add(username) else onlineUsers.remove(username)
                contactsAdapter.notifyDataSetChanged()
            }
        }
        SocketManager.onOnlineUsers.add { users ->
            runOnUiThread {
                onlineUsers.clear()
                onlineUsers.addAll(users)
                contactsAdapter.notifyDataSetChanged()
                tvStatus.text = "● Онлайн"
            }
        }
        SocketManager.onGroupCreated.add { group ->
            runOnUiThread {
                if (groups.none { it.id == group.id }) {
                    groups.add(group)
                    groupsAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val token = Prefs.token ?: return@launch

            val usersResult = withContext(Dispatchers.IO) {
                try { ApiClient.getUsers(token) } catch (e: Exception) { null }
            }
            usersResult?.users?.let { list ->
                contacts.clear()
                contacts.addAll(list)
                contactsAdapter.notifyDataSetChanged()
            }

            val groupsResult = withContext(Dispatchers.IO) {
                try { ApiClient.getGroups(token) } catch (e: Exception) { null }
            }
            groupsResult?.groups?.let { list ->
                groups.clear()
                groups.addAll(list)
                groupsAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showTab(showGroups: Boolean) {
        if (showGroups) {
            tabGroups.alpha = 1f; tabChats.alpha = 0.5f
            rvGroups.visibility = View.VISIBLE; rvContacts.visibility = View.GONE
        } else {
            tabChats.alpha = 1f; tabGroups.alpha = 0.5f
            rvContacts.visibility = View.VISIBLE; rvGroups.visibility = View.GONE
        }
    }

    private fun openChat(title: String, room: String, isGroup: Boolean) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("room", room)
            putExtra("title", title)
            putExtra("isGroup", isGroup)
        }
        startActivity(intent)
    }

    private fun launchCallActivity(call: CallInfo) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("callId", call.callId)
            putExtra("peer", call.peer)
            putExtra("type", call.type.name)
            putExtra("isIncoming", call.isIncoming)
            putExtra("isGroup", call.isGroup)
        }
        startActivity(intent)
    }

    private fun showCreateGroupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_group, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.groupNameInput)
        val membersList = dialogView.findViewById<LinearLayout>(R.id.membersList)
        val checkBoxes = mutableMapOf<String, CheckBox>()

        contacts.forEach { user ->
            val cb = CheckBox(this).apply {
                text = user.username
                setTextColor(resources.getColor(android.R.color.white, null))
            }
            checkBoxes[user.username] = cb
            membersList.addView(cb)
        }

        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Создать группу")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) { Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val members = checkBoxes.entries.filter { it.value.isChecked }.map { it.key }
                createGroup(name, members)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createGroup(name: String, members: List<String>) {
        lifecycleScope.launch {
            val token = Prefs.token ?: return@launch
            val result = withContext(Dispatchers.IO) {
                try { ApiClient.createGroup(token, name, members) } catch (e: Exception) { null }
            }
            if (result != null) {
                if (groups.none { it.id == result.id }) groups.add(result)
                groupsAdapter.notifyDataSetChanged()
                showTab(true)
                Toast.makeText(this@MainActivity, "Группа «$name» создана!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.onIncomingCall.clear()
        SocketManager.onUserStatus.clear()
        SocketManager.onOnlineUsers.clear()
        SocketManager.onGroupCreated.clear()
    }
}
