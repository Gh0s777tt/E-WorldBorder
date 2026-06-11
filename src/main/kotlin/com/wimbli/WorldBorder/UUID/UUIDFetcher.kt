package com.wimbli.WorldBorder.UUID

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * Looks up Mojang UUIDs/names.
 *
 * Fixes vs. the original:
 *  - caches are [ConcurrentHashMap] (the old plain HashMaps were mutated from a thread pool)
 *  - the removed Mojang "name history" endpoint is replaced by the server's own OfflinePlayer cache,
 *    with the session-server profile endpoint as a network fallback
 *  - blocking lookups are never run on the caller's thread unless they explicitly ask for the sync variant
 */
object UUIDFetcher {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(UUID::class.java, UUIDTypeAdapter())
        .create()

    // name -> UUID; the modern Mojang endpoint returns the current profile (the old "?at=" form is gone)
    private const val UUID_URL = "https://api.mojang.com/users/profiles/minecraft/%s"
    // UUID -> current profile (name); replaces the removed /user/profiles/<uuid>/names endpoint
    private const val PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s"

    private val uuidCache = ConcurrentHashMap<String, UUID>()
    private val nameCache = ConcurrentHashMap<UUID, String>()

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    // daemon thread pool so async lookups never keep the JVM alive or block the main server thread
    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "WorldBorder-UUIDFetcher").apply { isDaemon = true }
    }

    // ----- async helpers (preferred; never block the calling thread) -----

    fun getUUID(name: String, action: Consumer<UUID?>) {
        pool.execute { action.accept(getUUID(name)) }
    }

    fun getName(uuid: UUID, action: Consumer<String?>) {
        pool.execute { action.accept(getName(uuid)) }
    }

    // ----- synchronous lookups (may hit the network; do not call on the main thread) -----

    fun getUUID(name: String): UUID? {
        val key = name.lowercase()
        uuidCache[key]?.let { return it }

        // try the server's local player cache first (no network)
        Bukkit.getOfflinePlayerIfCached(name)?.let { cached ->
            uuidCache[key] = cached.uniqueId
            cached.name?.let { nameCache[cached.uniqueId] = it }
            return cached.uniqueId
        }

        return try {
            val obj = httpGet(String.format(UUID_URL, name)) ?: return null
            val id = obj.get("id")?.asString ?: return null
            val realName = obj.get("name")?.asString ?: name
            val uuid = UUIDTypeAdapter.fromString(id)
            uuidCache[key] = uuid
            nameCache[uuid] = realName
            uuid
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getName(uuid: UUID): String? {
        nameCache[uuid]?.let { return it }

        // the old Mojang names endpoint is gone; use the server's own usercache (no network) first
        Bukkit.getOfflinePlayer(uuid).name?.let { name ->
            nameCache[uuid] = name
            uuidCache[name.lowercase()] = uuid
            return name
        }

        return try {
            val obj = httpGet(String.format(PROFILE_URL, UUIDTypeAdapter.fromUUID(uuid))) ?: return null
            val name = obj.get("name")?.asString ?: return null
            nameCache[uuid] = name
            uuidCache[name.lowercase()] = uuid
            name
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getNameList(uuids: List<UUID>): Map<UUID, String> {
        val map = HashMap<UUID, String>()
        for (uuid in uuids) {
            getName(uuid)?.let { map[uuid] = it }
        }
        return map
    }

    private fun httpGet(url: String): JsonObject? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null
        val body = response.body()
        if (body.isNullOrEmpty()) return null
        return gson.fromJson(body, JsonObject::class.java)
    }
}
