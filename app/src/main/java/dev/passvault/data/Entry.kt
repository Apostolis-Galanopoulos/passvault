package dev.passvault.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    // Keep secrets out of logs and crash reports.
    override fun toString() = "Entry(id=$id, title=$title)"
}

/** Plaintext serialization of the vault contents. Only ever stored encrypted. */
object VaultCodec {
    private const val FORMAT = 1

    fun encode(entries: List<Entry>): ByteArray {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("username", e.username)
                    .put("password", e.password)
                    .put("url", e.url)
                    .put("notes", e.notes)
                    .put("updatedAt", e.updatedAt)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("entries", array)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): List<Entry> {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val array = root.getJSONArray("entries")
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Entry(
                id = o.getString("id"),
                title = o.optString("title"),
                username = o.optString("username"),
                password = o.optString("password"),
                url = o.optString("url"),
                notes = o.optString("notes"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
    }
}
