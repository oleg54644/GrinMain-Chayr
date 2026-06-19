package com.grinmain.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.grinmain.R
import com.grinmain.data.User

class ContactsAdapter(
    private val contacts: List<User>,
    private val onlineUsers: Set<String>,
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: TextView = v.findViewById(R.id.tvAvatar)
        val name: TextView = v.findViewById(R.id.tvName)
        val status: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false))

    override fun getItemCount() = contacts.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = contacts[position]
        val isOnline = user.username in onlineUsers
        holder.avatar.text = user.username.first().uppercaseChar().toString()
        holder.name.text = user.username
        holder.status.text = if (isOnline) "● Онлайн" else "○ Офлайн"
        holder.status.setTextColor(
            if (isOnline) 0xFF3FB950.toInt() else 0xFF8B949E.toInt()
        )
        holder.itemView.setOnClickListener { onClick(user) }
    }
}

class GroupsAdapter(
    private val groups: List<com.grinmain.data.Group>,
    private val onClick: (com.grinmain.data.Group) -> Unit
) : RecyclerView.Adapter<GroupsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: TextView = v.findViewById(R.id.tvAvatar)
        val name: TextView = v.findViewById(R.id.tvName)
        val status: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false))

    override fun getItemCount() = groups.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val group = groups[position]
        holder.avatar.text = "#"
        holder.name.text = group.name
        holder.status.text = "${group.members.size} участников"
        holder.status.setTextColor(0xFF8B949E.toInt())
        holder.itemView.setOnClickListener { onClick(group) }
    }
}

class MessagesAdapter(
    private val messages: List<com.grinmain.data.Message>,
    private val myUsername: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object { const val TYPE_ME = 0; const val TYPE_OTHER = 1 }

    override fun getItemViewType(position: Int) =
        if (messages[position].from == myUsername) TYPE_ME else TYPE_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == TYPE_ME) R.layout.item_message_me else R.layout.item_message_other
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        holder.itemView.findViewById<TextView>(R.id.tvMessageText).text = msg.text
        holder.itemView.findViewById<TextView>(R.id.tvMessageTime).text = msg.ts
        if (getItemViewType(position) == TYPE_OTHER) {
            holder.itemView.findViewById<TextView>(R.id.tvSender)?.text = msg.from
        }
    }
}
