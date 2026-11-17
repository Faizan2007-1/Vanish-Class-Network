
package com.example.vanish

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class Conn {
    OFFLINE,
    CONNECTING,
    ONLINE
}

class RelayClient(
    private val name: () -> String,
    private val deviceId: () -> String,
    private val onText: (String) -> Unit,
    private val onOnline: () -> Unit
) {
    val state = MutableStateFlow(Conn.OFFLINE)

    private val relayUrl =
        "wss://vanish-relay-sawaira.bonto.page/ws"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var wanted = false

    @Volatile
    private var socket: WebSocket? = null

    private var loopJob: Job? = null
    private var heartbeatJob: Job? = null

    private val lastPong = AtomicLong(0L)

    @Synchronized
    fun start() {
        if (wanted) return

        wanted = true

        loopJob = scope.launch {
            connectionLoop()
        }
    }

    private suspend fun connectionLoop() {
        var reconnectDelay = 1_000L

        while (scope.isActive && wanted) {
            state.value = Conn.CONNECTING

            val ended = CompletableDeferred<Unit>()

            val request = Request.Builder()
                .url(relayUrl)
                .build()

            val listener = object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    socket = webSocket

                    lastPong.set(System.currentTimeMillis())

                    val join = JSONObject()
                        .put("type", "join")
                        .put("protocol", 1)
                        .put("device_id", deviceId())
                        .put("name", name().trim().take(30))

                    webSocket.send(join.toString())

                    heartbeatJob?.cancel()

                    heartbeatJob = scope.launch {
                        while (isActive && wanted) {
                            delay(15_000L)

                            val currentSocket = socket ?: break
                            val now = System.currentTimeMillis()

                            if (now - lastPong.get() > 30_000L) {
                                currentSocket.close(
                                    1001,
                                    "Heartbeat timeout"
                                )
                                break
                            }

                            currentSocket.send(
                                JSONObject()
                                    .put("type", "ping")
                                    .toString()
                            )
                        }
                    }
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    try {
                        val frame = JSONObject(text)
                        val type = frame.optString("type")

                        when (type) {
                            "welcome" -> {
                                state.value = Conn.ONLINE
                                reconnectDelay = 1_000L
                                onOnline()
                            }

                            "pong" -> {
                                lastPong.set(
                                    System.currentTimeMillis()
                                )
                            }

                            "message", "expired", "delivered", "error" -> {
                                onText(text)
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore invalid JSON frames.
                    }
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    webSocket.close(code, reason)
                    ended.complete(Unit)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    ended.complete(Unit)
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    ended.complete(Unit)
                }
            }

            val newSocket = client.newWebSocket(request, listener)
            socket = newSocket

            ended.await()

            heartbeatJob?.cancel()
            heartbeatJob = null

            socket = null
            state.value = Conn.OFFLINE

            if (wanted) {
                delay(reconnectDelay)
                reconnectDelay =
                    (reconnectDelay * 2).coerceAtMost(8_000L)
            }
        }

        state.value = Conn.OFFLINE
    }

    fun send(frame: String): Boolean {
        if (state.value != Conn.ONLINE) return false

        val message = try {
            JSONObject(frame)
        } catch (_: Exception) {
            return false
        }

        if (message.optString("type") != "message") {
            return true
        }

        val id = message.optString("id")
        val text = message.optString("text")

        if (id.isBlank() || text.isBlank() || text.length > 500) {
            return false
        }

        val payload = JSONObject()
            .put("type", "message")
            .put("id", id)
            .put("sender", name())
            .put("text", text)

        return socket?.send(payload.toString()) ?: false
    }

    @Synchronized
    fun kick() {
        if (!wanted) {
            start()
        }
    }

    @Synchronized
    fun stop() {
        wanted = false

        heartbeatJob?.cancel()
        heartbeatJob = null

        socket?.close(1000, "App stopped")
        socket = null

        loopJob?.cancel()
        loopJob = null

        state.value = Conn.OFFLINE
    }
}
