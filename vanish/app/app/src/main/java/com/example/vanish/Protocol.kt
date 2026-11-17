
package com.example.vanish

import org.json.JSONObject

object Protocol {

    fun hello(name: String, deviceId: String) = JSONObject()
        .put("type", "hello")
        .put("version", Config.PROTOCOL_VERSION)
        .put("name", name)
        .put("device_id", deviceId)
        .toString()

    fun message(id: String, sender: String, text: String) = JSONObject()
        .put("type", "message")
        .put("id", id)
        .put("sender", sender)
        .put("text", text)
        .toString()

    fun delivered(id: String) = JSONObject()
        .put("type", "delivered")
        .put("id", id)
        .toString()

    fun ping() = JSONObject()
        .put("type", "ping")
        .toString()

    sealed class In {
        data class Welcome(val serverTime: Long) : In()

        data class Msg(
            val id: String,
            val sender: String,
            val text: String,
            val sentAt: Long,
            val expiresAt: Long
        ) : In()

        data class Expired(val id: String) : In()

        object Pong : In()
        object Unknown : In()
    }

    fun parse(raw: String): In = try {
        val json = JSONObject(raw)

        when (json.optString("type")) {
            "welcome" -> In.Welcome(json.getLong("server_time"))

            "message" -> In.Msg(
                json.getString("id"),
                json.getString("sender"),
                json.getString("text"),
                json.getLong("sent_at"),
                json.getLong("expires_at")
            )

            "expired" -> In.Expired(json.getString("id"))
            "pong" -> In.Pong
            else -> In.Unknown
        }
    } catch (e: Exception) {
        In.Unknown
    }
}
