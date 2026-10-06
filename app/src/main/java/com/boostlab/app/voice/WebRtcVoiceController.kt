package com.boostlab.app.voice

import android.content.Context
import android.media.AudioManager
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

data class VoiceIceServer(
    val urls: List<String>,
    val username: String = "",
    val credential: String = "",
)

class WebRtcVoiceController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private val pendingRemoteIce = mutableListOf<IceCandidate>()

    private var localUserId: String = ""
    private var peerUserId: String = ""
    private var signalSink: ((String, String) -> Unit)? = null
    private var stateSink: ((String, String?) -> Unit)? = null

    fun startOutgoing(
        localUserId: String,
        peerUserId: String,
        iceServers: List<VoiceIceServer>,
        signalSink: (String, String) -> Unit,
        stateSink: (String, String?) -> Unit,
    ) {
        prepare(localUserId, peerUserId, iceServers, signalSink, stateSink)
        state("CALLING", null)

        val connection = requireNotNull(peer)
        connection.createOffer(
            object : SimpleSdpObserver() {
                override fun onCreateSuccess(description: SessionDescription) {
                    connection.setLocalDescription(
                        object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                sendDescription("voice_offer", description)
                            }

                            override fun onSetFailure(error: String) {
                                fail("Local offer failed: $error")
                            }
                        },
                        description,
                    )
                }

                override fun onCreateFailure(error: String) {
                    fail("Offer failed: $error")
                }
            },
            audioOfferConstraints(),
        )
    }

    fun acceptIncoming(
        localUserId: String,
        peerUserId: String,
        offerPayload: String,
        iceServers: List<VoiceIceServer>,
        signalSink: (String, String) -> Unit,
        stateSink: (String, String?) -> Unit,
    ) {
        prepare(localUserId, peerUserId, iceServers, signalSink, stateSink)
        state("CONNECTING", null)

        val payload = JSONObject(offerPayload)
        val sdp = payload.getString("sdp")
        val connection = requireNotNull(peer)
        connection.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    flushPendingIce()
                    connection.createAnswer(
                        object : SimpleSdpObserver() {
                            override fun onCreateSuccess(description: SessionDescription) {
                                connection.setLocalDescription(
                                    object : SimpleSdpObserver() {
                                        override fun onSetSuccess() {
                                            sendDescription("voice_answer", description)
                                        }

                                        override fun onSetFailure(error: String) {
                                            fail("Local answer failed: $error")
                                        }
                                    },
                                    description,
                                )
                            }

                            override fun onCreateFailure(error: String) {
                                fail("Answer failed: $error")
                            }
                        },
                        audioOfferConstraints(),
                    )
                }

                override fun onSetFailure(error: String) {
                    fail("Remote offer failed: $error")
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, sdp),
        )
    }

    fun handleSignal(sender: String, type: String, payloadText: String) {
        if (sender == localUserId || peerUserId.isBlank()) return

        val payload = runCatching { JSONObject(payloadText) }.getOrNull() ?: return
        val target = payload.optString("target")
        if (target.isNotBlank() && target != localUserId) return
        if (sender != peerUserId) return

        when (type) {
            "voice_answer" -> {
                val sdp = payload.optString("sdp")
                if (sdp.isBlank()) return
                peer?.setRemoteDescription(
                    object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            flushPendingIce()
                            state("CONNECTING", null)
                        }

                        override fun onSetFailure(error: String) {
                            fail("Remote answer failed: $error")
                        }
                    },
                    SessionDescription(SessionDescription.Type.ANSWER, sdp),
                )
            }

            "voice_ice" -> {
                val candidateText = payload.optString("candidate")
                if (candidateText.isBlank()) return
                val candidate = IceCandidate(
                    payload.optString("sdp_mid").ifBlank { null },
                    payload.optInt("sdp_mline_index", 0),
                    candidateText,
                )
                val connection = peer
                if (connection == null || connection.remoteDescription == null) {
                    pendingRemoteIce += candidate
                } else {
                    connection.addIceCandidate(candidate)
                }
            }

            "voice_hangup" -> closePeer(sendHangup = false)
        }
    }

    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    fun hangup() {
        closePeer(sendHangup = true)
    }

    fun release() {
        closePeer(sendHangup = false)
        factory?.dispose()
        factory = null
    }

    private fun prepare(
        localUserId: String,
        peerUserId: String,
        iceServers: List<VoiceIceServer>,
        signalSink: (String, String) -> Unit,
        stateSink: (String, String?) -> Unit,
    ) {
        closePeer(sendHangup = false)
        this.localUserId = localUserId
        this.peerUserId = peerUserId
        this.signalSink = signalSink
        this.stateSink = stateSink

        ensureFactory()
        configureAudioMode()

        audioSource = factory!!.createAudioSource(MediaConstraints())
        audioTrack = factory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource).apply {
            setEnabled(true)
        }

        val rtcIceServers = iceServers
            .flatMap { server ->
                server.urls.map { url ->
                    PeerConnection.IceServer.builder(url).apply {
                        if (server.username.isNotBlank()) setUsername(server.username)
                        if (server.credential.isNotBlank()) setPassword(server.credential)
                    }.createIceServer()
                }
            }
            .ifEmpty {
                listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                )
            }
        val rtcConfig = PeerConnection.RTCConfiguration(rtcIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peer = factory!!.createPeerConnection(rtcConfig, observer())
            ?: error("Unable to create WebRTC peer connection")

        peer!!.addTrack(requireNotNull(audioTrack), listOf(AUDIO_STREAM_ID))
    }

    private fun ensureFactory() {
        if (factory != null) return

        val options = PeerConnectionFactory.InitializationOptions
            .builder(appContext)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        factory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
    }

    private fun observer(): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            when (newState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                -> state("CONNECTED", null)

                PeerConnection.IceConnectionState.CHECKING -> state("CONNECTING", null)
                PeerConnection.IceConnectionState.DISCONNECTED -> state("RECONNECTING", null)
                PeerConnection.IceConnectionState.FAILED -> fail("ICE connection failed")
                PeerConnection.IceConnectionState.CLOSED -> state("IDLE", null)
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidate(candidate: IceCandidate) {
            val payload = JSONObject()
                .put("target", peerUserId)
                .put("sdp_mid", candidate.sdpMid ?: "")
                .put("sdp_mline_index", candidate.sdpMLineIndex)
                .put("candidate", candidate.sdp)
                .toString()
            signalSink?.invoke("voice_ice", payload)
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit
    }

    private fun flushPendingIce() {
        val connection = peer ?: return
        if (connection.remoteDescription == null) return
        pendingRemoteIce.forEach { connection.addIceCandidate(it) }
        pendingRemoteIce.clear()
    }

    private fun sendDescription(type: String, description: SessionDescription) {
        val payload = JSONObject()
            .put("target", peerUserId)
            .put("sdp", description.description)
            .toString()
        signalSink?.invoke(type, payload)
    }

    private fun closePeer(sendHangup: Boolean) {
        if (sendHangup && peerUserId.isNotBlank()) {
            val payload = JSONObject()
                .put("target", peerUserId)
                .toString()
            signalSink?.invoke("voice_hangup", payload)
        }

        runCatching { peer?.close() }
        runCatching { peer?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { audioSource?.dispose() }

        peer = null
        audioTrack = null
        audioSource = null
        pendingRemoteIce.clear()

        restoreAudioMode()
        if (stateSink != null) {
            state("IDLE", null)
        }
    }

    private fun configureAudioMode() {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
        }
    }

    private fun restoreAudioMode() {
        runCatching {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun audioOfferConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }

    private fun fail(message: String) {
        state("FAILED", message)
    }

    private fun state(value: String, error: String?) {
        stateSink?.invoke(value, error)
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    companion object {
        private const val AUDIO_TRACK_ID = "BOOSTLAB_AUDIO_TRACK"
        private const val AUDIO_STREAM_ID = "BOOSTLAB_AUDIO"
    }
}
