
package com.example.vanish

object Config {

    // Teacher's FastAPI server address
    const val BASE_URL = "http://10.11.0.54:8000/"

    // Existing app settings
    const val PROTOCOL_VERSION = 1
    const val TTL_MS = 30_000L
    const val OUTBOX_MAX_AGE_MS = TTL_MS
    const val HEARTBEAT_MS = 10_000L
    const val HEARTBEAT_TIMEOUT_MS = 25_000L
    const val RECONNECT_MAX_MS = 15_000L
    const val SEEN_GRACE_MS = 60_000L

    const val BLOCK_SCREENSHOTS = false

    // REST API polling
    const val SYNC_INTERVAL_MS = 3_000L
}
