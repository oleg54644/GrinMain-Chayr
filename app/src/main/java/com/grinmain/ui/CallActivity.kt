package com.grinmain.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.grinmain.R
import com.grinmain.call.WebRtcManager
import com.grinmain.data.*
import com.grinmain.network.ApiClient
import com.grinmain.network.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.SurfaceViewRenderer
import org.webrtc.EglBase

class CallActivity : AppCompatActivity() {

    private lateinit var tvCallTitle: TextView
    private lateinit var tvCallStatus: TextView
    private lateinit var tvCallTimer: Chronometer
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var remotesContainer: LinearLayout
    private lateinit var btnEndCall: ImageButton
    private lateinit var btnMute: ImageButton
    private lateinit var btnCamera: ImageButton
    private lateinit var btnSwitchCam: ImageButton
    private lateinit var btnAccept: ImageButton
    private lateinit var btnReject: ImageButton
    private lateinit var incomingPanel: LinearLayout
    private lateinit var callControls: LinearLayout

    private var callId = ""
    private var peer = ""
    private var isGroup = false
    private var isIncoming = false
    private var callType = CallType.AUDIO
    private var webRtcManager: WebRtcManager? = null
    private var isMuted = false
    private var isCameraOff = false
    private var peers = mutableListOf<String>()

    private val eglBase by lazy { EglBase.create() }

    companion object {
        private const val PERM_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        callId = intent.getStringExtra("callId") ?: ""
        peer = intent.getStringExtra("peer") ?: ""
        isGroup = intent.getBooleanExtra("isGroup", false)
        isIncoming = intent.getBooleanExtra("isIncoming", false)
        callType = if (intent.getStringExtra("type") == "VIDEO") CallType.VIDEO else CallType.AUDIO

        tvCallTitle = findViewById(R.id.tvCallTitle)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        tvCallTimer = findViewById(R.id.tvCallTimer)
        localRenderer = findViewById(R.id.localRenderer)
        remoteRenderer = findViewById(R.id.remoteRenderer)
        remotesContainer = findViewById(R.id.remotesContainer)
        btnEndCall = findViewById(R.id.btnEndCall)
        btnMute = findViewById(R.id.btnMute)
        btnCamera = findViewById(R.id.btnCamera)
        btnSwitchCam = findViewById(R.id.btnSwitchCam)
        btnAccept = findViewById(R.id.btnAccept)
        btnReject = findViewById(R.id.btnReject)
        incomingPanel = findViewById(R.id.incomingPanel)
        callControls = findViewById(R.id.callControls)

        tvCallTitle.text = peer
        tvCallStatus.text = if (isIncoming) "Входящий звонок…" else "Соединение…"

        // Video renderers
        localRenderer.init(eglBase.eglBaseContext, null)
        remoteRenderer.init(eglBase.eglBaseContext, null)

        if (callType == CallType.AUDIO) {
            localRenderer.visibility = View.GONE
            remoteRenderer.visibility = View.GONE
            btnCamera.visibility = View.GONE
            btnSwitchCam.visibility = View.GONE
        }

        if (isIncoming) {
            incomingPanel.visibility = View.VISIBLE
            callControls.visibility = View.GONE
            btnAccept.setOnClickListener { acceptCall() }
            btnReject.setOnClickListener { rejectCall() }
        } else {
            incomingPanel.visibility = View.GONE
            callControls.visibility = View.VISIBLE
            checkPermissionsAndStart()
        }

        btnEndCall.setOnClickListener { endCall() }
        btnMute.setOnClickListener { toggleMute() }
        btnCamera.setOnClickListener { toggleCamera() }
        btnSwitchCam.setOnClickListener { webRtcManager?.switchCamera() }

        // Socket callbacks
        SocketManager.onCallAccepted.add { id ->
            if (id == callId || callId.isEmpty()) {
                this.callId = id
                runOnUiThread { onCallConnected() }
            }
        }
        SocketManager.onCallRejected.add { _ ->
            runOnUiThread { endCallUi("Звонок отклонён") }
        }
        SocketManager.onCallEnded.add { _ ->
            runOnUiThread { endCallUi("Звонок завершён") }
        }

        // Outgoing: emit call request
        if (!isIncoming && callId.isEmpty()) {
            SocketManager.requestCall(peer, callType.name.lowercase(), isGroup, peers)
        }
    }

    private fun checkPermissionsAndStart() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (callType == CallType.VIDEO) perms.add(Manifest.permission.CAMERA)
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) initWebRtc()
        else ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initWebRtc()
        } else {
            Toast.makeText(this, "Нужны разрешения для звонка", Toast.LENGTH_SHORT).show()
            endCallUi("")
        }
    }

    private fun initWebRtc() {
        val allPeers = if (peers.isEmpty()) listOf(peer) else peers
        webRtcManager = WebRtcManager(
            context = this,
            callId = callId,
            peers = allPeers,
            isInitiator = !isIncoming,
            onLocalStream = { track ->
                runOnUiThread {
                    track?.addSink(localRenderer)
                    localRenderer.visibility = if (callType == CallType.VIDEO) View.VISIBLE else View.GONE
                }
            },
            onRemoteStream = { remotePeer, track ->
                runOnUiThread {
                    if (callType == CallType.VIDEO) {
                        track?.addSink(remoteRenderer)
                        remoteRenderer.visibility = View.VISIBLE
                    }
                }
            }
        )
        webRtcManager?.init(callType == CallType.VIDEO)
    }

    private fun acceptCall() {
        SocketManager.answerCall(callId, true)
        incomingPanel.visibility = View.GONE
        callControls.visibility = View.VISIBLE
        onCallConnected()
        checkPermissionsAndStart()
    }

    private fun rejectCall() {
        SocketManager.answerCall(callId, false)
        finish()
    }

    private fun onCallConnected() {
        tvCallStatus.text = if (callType == CallType.VIDEO) "Видеозвонок" else "Аудиозвонок"
        tvCallTimer.base = SystemClock.elapsedRealtime()
        tvCallTimer.start()
        tvCallTimer.visibility = View.VISIBLE
    }

    private fun toggleMute() {
        isMuted = !isMuted
        webRtcManager?.toggleMute(isMuted)
        btnMute.setImageResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic)
    }

    private fun toggleCamera() {
        isCameraOff = !isCameraOff
        webRtcManager?.toggleCamera(!isCameraOff)
        btnCamera.setImageResource(if (isCameraOff) R.drawable.ic_videocam_off else R.drawable.ic_videocam)
    }

    private fun endCall() {
        SocketManager.endCall(callId)
        endCallUi("")
    }

    private fun endCallUi(reason: String) {
        tvCallTimer.stop()
        webRtcManager?.release()
        if (reason.isNotEmpty()) Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.onCallAccepted.clear()
        SocketManager.onCallRejected.clear()
        SocketManager.onCallEnded.clear()
        SocketManager.onWebRtcOffer.clear()
        SocketManager.onWebRtcAnswer.clear()
        SocketManager.onWebRtcIce.clear()
        try {
            localRenderer.release()
            remoteRenderer.release()
            eglBase.release()
        } catch (_: Exception) {}
    }
}
