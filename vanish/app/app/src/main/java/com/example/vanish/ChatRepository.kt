package com.example.vanish

import android.content.Context
import androidx.room.withTransaction
import com.example.vanish.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Owns the message lifecycle:
 *   created -> waiting (outbox) -> sent -> [relay echo = delivered] -> displayed -> expired (row deleted)
 *   or waiting -> dropped (too old while offline).
 */
class ChatRepository(private val ctx: Context) {
    private val db = AppDatabase.get(ctx)
    private val dao = db.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val prefs = ctx.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val sentThisSession = HashSet<String>()

    val messages: Flow<List<MessageEntity>> = dao.observeMessages()
    val outbox: Flow<List<OutboxEntity>> = dao.observeOutbox()
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val deviceId: String = prefs.getString("device_id", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    var name: String = prefs.getString("name", "") ?: ""
        private set

    private val client = RelayClient({ name }, { deviceId }, ::onFrame, ::onOnline)
    val connection: StateFlow<Conn> get() = client.state
    private var sweeper: Job? = null

    fun hasName() = name.isNotBlank()

    fun join(displayName: String) {
        name = displayName.trim()
        prefs.edit().putString("name", name).apply()
        start()
    }

    fun start() {
        client.start()
        if (sweeper?.isActive != true) sweeper = scope.launch {
            while (isActive) { sweepOnce(); delay(500) }
        }
    }

    fun kick() { client.kick(); scope.launch { sweepOnce() } }

    /** Deletes expired rows (REAL deletion) and drops stale outbox entries. Safe to call anytime. */
    suspend fun sweepOnce() = lock.withLock {
        val now = ClockSync.now()
        dao.deleteExpired(now)
        dao.purgeSeen(now - Config.SEEN_GRACE_MS)
        val stale = dao.staleOutboxIds(System.currentTimeMillis() - Config.OUTBOX_MAX_AGE_MS)
        if (stale.isNotEmpty()) {
            stale.forEach { dao.deleteOutbox(it) }      // text is deleted too: state DROPPED
            notices.tryEmit("${stale.size} message(s) not sent: waited longer than ${Config.OUTBOX_MAX_AGE_MS / 1000}s offline")
        }
    }

    /** User pressed send. Always goes through the outbox, so online and offline paths are identical. */
    fun send(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        scope.launch {
            dao.insertOutbox(OutboxEntity(UUID.randomUUID().toString(), t, System.currentTimeMillis()))
            flushOutbox()
        }
    }

    private fun onOnline() { scope.launch { lock.withLock { sentThisSession.clear() }; flushOutbox() } }

    private suspend fun flushOutbox() = lock.withLock {
        for (o in dao.outboxAll()) {
            if (o.id in sentThisSession) continue          // never write the same id twice per connection
            if (client.send(Protocol.message(o.id, name, o.text))) sentThisSession.add(o.id)
            else break                                      // offline: keep everything queued
        }
    }

    private fun onFrame(raw: String) {
        when (val m = Protocol.parse(raw)) {
            is Protocol.In.Welcome -> ClockSync.update(m.serverTime)
            is Protocol.In.Msg -> scope.launch { handleMessage(m) }
            is Protocol.In.Expired -> scope.launch { lock.withLock { dao.deleteMessage(m.id) } }
            else -> {}
        }
    }

    private suspend fun handleMessage(m: Protocol.In.Msg) = lock.withLock {
        if (m.expiresAt <= ClockSync.now()) return@withLock           // expired/replayed: ignore, never store
        var stored = false
        db.withTransaction {
            if (dao.insertSeen(SeenEntity(m.id, m.expiresAt)) == -1L) return@withTransaction  // duplicate id
            val mine = dao.inOutbox(m.id) > 0
            dao.insertMessage(MessageEntity(m.id, m.sender, m.text, m.sentAt, m.expiresAt, mine))
            dao.deleteOutbox(m.id)                                    // relay echo = confirmed sent
            stored = !mine
        }
        if (stored) client.send(Protocol.delivered(m.id))             // delivery confirmation
    }
}
