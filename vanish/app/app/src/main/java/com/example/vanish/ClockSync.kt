package com.example.vanish

import android.content.Context
import android.os.SystemClock

/**
 * Relay time. serverNow() = last relay time + monotonic time elapsed since.
 * Never reads the phone's wall clock once synced, so a wrong phone clock (or the user changing it
 * mid-countdown) cannot change a countdown. The offset is persisted so a cold start before the
 * first connection is still approximately right.
 */
object ClockSync {
    @Volatile private var anchorServer = 0L
    @Volatile private var anchorElapsed = 0L
    @Volatile private var synced = false
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("clock", Context.MODE_PRIVATE)
        val off = prefs!!.getLong("offset", Long.MIN_VALUE)
        if (off != Long.MIN_VALUE) {          // fallback until the relay answers
            anchorServer = System.currentTimeMillis() + off
            anchorElapsed = SystemClock.elapsedRealtime()
            synced = true
        }
    }

    fun update(serverMs: Long) {
        anchorServer = serverMs
        anchorElapsed = SystemClock.elapsedRealtime()
        synced = true
        prefs?.edit()?.putLong("offset", serverMs - System.currentTimeMillis())?.apply()
    }

    fun now(): Long =
        if (!synced) System.currentTimeMillis()
        else anchorServer + (SystemClock.elapsedRealtime() - anchorElapsed)
}
