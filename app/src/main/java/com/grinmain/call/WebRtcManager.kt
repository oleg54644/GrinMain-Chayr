package com.grinmain.call

import android.content.Context
import android.util.Log
import com.grinmain.network.SocketManager
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val callId: String,
    private val peers: List<String>,          // usernames of remote peers
    private val isInitiator: Boolean,
    private val onLocalStream: (VideoTrack?) -> Unit,
    private val onRemoteStream: (String, VideoTrack?) -> Unit   // peer -> track
) {
    private val TAG = "WebRtcManager"

    private lateinit var factory: PeerConnectionFactory
    private val peerConnections = mutableMapOf<String, PeerConnection>()
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var localStream: MediaStream? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    private val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
    }

    fun init(enableVideo: Boolean) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext, true, true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        // Audio source
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
        }
        val audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)

        // Video source
        if (enableVideo) {
            videoCapturer = createCameraCapturer()
            if (videoCapturer != null) {
                surfaceHelper = SurfaceTextureHelper.create("CaptureThread", EglBase.create().eglBaseContext)
                val videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
                videoCapturer!!.initialize(surfaceHelper, context, videoSource.capturerObserver)
                videoCapturer!!.startCapture(640, 480, 30)
                localVideoTrack = factory.createVideoTrack("video0", videoSource)
                onLocalStream(localVideoTrack)
            }
        }

        // Create streams
        localStream = factory.createLocalMediaStream("localStream").apply {
            localAudioTrack?.let { addTrack(it) }
            localVideoTrack?.let { addTrack(it) }
        }

        // Setup socket listeners
        setupSignalListeners()

        // Create peer connections for all peers
        peers.forEach { peer ->
            createPeerConnection(peer)
        }

        // Initiator sends offers
        if (isInitiator) {
            peers.forEach { peer -> createOffer(peer) }
        }
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        // Front camera first
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        for (name in enumerator.deviceNames) {
            if (!enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return null
    }

    private fun createPeerConnection(peer: String): PeerConnection? {
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val json = "{\"sdpMid\":\"${candidate.sdpMid}\",\"sdpMLineIndex\":${candidate.sdpMLineIndex},\"candidate\":\"${candidate.sdp}\"}"
                SocketManager.sendIceCandidate(peer, json, callId)
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "Remote stream from $peer: ${stream.videoTracks.size} video tracks")
                val videoTrack = stream.videoTracks.firstOrNull()
                onRemoteStream(peer, videoTrack)
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) {
                    onRemoteStream(peer, track)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE $peer: $state")
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dc: DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }

        val pc = factory.createPeerConnection(rtcConfig, observer) ?: return null
        localStream?.let { pc.addStream(it) }
        peerConnections[peer] = pc
        return pc
    }

    private fun createOffer(peer: String) {
        val pc = peerConnections[peer] ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        SocketManager.sendOffer(peer, sdp.description, callId)
                    }
                    override fun onCreateSuccess(p0: SessionDescription) {}
                    override fun onSetFailure(e: String) { Log.e(TAG, "setLocal fail: $e") }
                    override fun onCreateFailure(e: String) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(e: String) { Log.e(TAG, "createOffer fail: $e") }
            override fun onSetFailure(e: String) {}
        }, constraints)
    }

    private fun setupSignalListeners() {
        SocketManager.onWebRtcOffer.add { from, sdpStr, _ ->
            Log.d(TAG, "Got offer from $from")
            var pc = peerConnections[from]
            if (pc == null) { pc = createPeerConnection(from) }
            pc?.let { conn ->
                val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                conn.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() { createAnswer(from, conn) }
                    override fun onCreateSuccess(p0: SessionDescription) {}
                    override fun onSetFailure(e: String) { Log.e(TAG, "setRemote fail: $e") }
                    override fun onCreateFailure(e: String) {}
                }, sdp)
            }
        }

        SocketManager.onWebRtcAnswer.add { from, sdpStr, _ ->
            Log.d(TAG, "Got answer from $from")
            val pc = peerConnections[from] ?: return@add
            val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { Log.d(TAG, "Remote answer set for $from") }
                override fun onCreateSuccess(p0: SessionDescription) {}
                override fun onSetFailure(e: String) { Log.e(TAG, "setRemote answer fail: $e") }
                override fun onCreateFailure(e: String) {}
            }, sdp)
        }

        SocketManager.onWebRtcIce.add { from, candidateJson, _ ->
            try {
                val pc = peerConnections[from] ?: return@add
                val obj = org.json.JSONObject(candidateJson)
                val candidate = IceCandidate(
                    obj.getString("sdpMid"),
                    obj.getInt("sdpMLineIndex"),
                    obj.getString("candidate")
                )
                pc.addIceCandidate(candidate)
            } catch (e: Exception) { Log.e(TAG, "ICE add fail: ${e.message}") }
        }
    }

    private fun createAnswer(peer: String, pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        SocketManager.sendAnswer(peer, sdp.description, callId)
                    }
                    override fun onCreateSuccess(p0: SessionDescription) {}
                    override fun onSetFailure(e: String) { Log.e(TAG, "setLocal answer fail: $e") }
                    override fun onCreateFailure(e: String) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(e: String) { Log.e(TAG, "createAnswer fail: $e") }
            override fun onSetFailure(e: String) {}
        }, constraints)
    }

    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun release() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            surfaceHelper?.dispose()
            peerConnections.values.forEach { it.close() }
            peerConnections.clear()
            factory.dispose()
        } catch (e: Exception) { Log.e(TAG, "release: ${e.message}") }
    }
}
