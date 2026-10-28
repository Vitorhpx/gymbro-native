package com.gymbro.local.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

object SemVer {
    private val pattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
    fun isNewer(candidate: String, current: String): Boolean {
        val a = pattern.matchEntire(candidate)?.groupValues?.drop(1)?.map(String::toInt) ?: return false
        val b = pattern.matchEntire(current)?.groupValues?.drop(1)?.map(String::toInt) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
}

data class ReleaseAsset(val name: String, val url: String, val sha256: String?)
data class AppRelease(val tagName: String, val apk: ReleaseAsset?)
enum class ApkIntegrity { STRUCTURE_VALIDATED, SHA256_VERIFIED }
data class DownloadedApk(val file: File, val integrity: ApkIntegrity)

@Serializable private data class ApiRelease(val tag_name: String, val assets: List<ApiAsset> = emptyList())
@Serializable private data class ApiAsset(
    val name: String,
    val browser_download_url: String,
    val digest: String? = null,
)

class ReleaseParser(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun parse(body: String): AppRelease = try {
        val response = json.decodeFromString<ApiRelease>(body)
        val apk = response.assets.asSequence()
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .sortedWith(compareBy<ApiAsset>({ !it.name.contains("universal", ignoreCase = true) }, { it.name.lowercase() }))
            .firstOrNull()
            ?.let { asset ->
                val digest = asset.digest?.takeIf { it.startsWith("sha256:", true) }?.substringAfter(':')?.lowercase()
                ReleaseAsset(asset.name, asset.browser_download_url, digest)
            }
        AppRelease(response.tag_name, apk)
    } catch (e: SerializationException) {
        throw UpdateException("GitHub returned an unreadable release.", e)
    } catch (e: IllegalArgumentException) {
        throw UpdateException("GitHub returned an unreadable release.", e)
    }
}

object UpdateUrlPolicy {
    private val redirectHosts = setOf("githubusercontent.com", "release-assets.githubusercontent.com")
    fun isAllowed(value: String): Boolean = runCatching {
        val uri = URI(value)
        if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null) return false
        when (uri.host?.lowercase()) {
            "github.com" -> uri.path.startsWith("/Vitorhpx/gymbro-native/releases/download/")
            in redirectHosts -> uri.path.isNotBlank() && uri.path != "/"
            else -> false
        }
    }.getOrDefault(false)
}

object DigestVerifier {
    fun matches(file: File, expectedSha256: String?): Boolean {
        if (expectedSha256 == null) return true
        if (!expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return MessageDigest.isEqual(digest.digest(), expectedSha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    }
}

object ApkStructureValidator {
    private val zipMagic = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

    fun isValid(file: File): Boolean = runCatching {
        if (file.length() < zipMagic.size) return false
        val header = file.inputStream().use { input ->
            ByteArray(zipMagic.size).also { input.read(it) }
        }
        if (!header.contentEquals(zipMagic)) return false
        ZipFile(file).use { zip ->
            zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null
        }
    }.getOrDefault(false)
}

class UpdateException(message: String, cause: Throwable? = null) : IOException(message, cause)

interface AppUpdateService {
    suspend fun latestRelease(): AppRelease
    suspend fun download(asset: ReleaseAsset, destination: File): DownloadedApk
}

class GitHubAppUpdateService(
    private val parser: ReleaseParser = ReleaseParser(),
    private val connectionFactory: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
    private val maxDownloadBytes: Long = MAX_APK_BYTES,
) : AppUpdateService {
    override suspend fun latestRelease(): AppRelease {
        val connection = connectionFactory(LATEST_RELEASE_ENDPOINT)
        return try {
            configure(connection, "application/vnd.github+json")
            if (connection.responseCode !in 200..299) throw UpdateException("Update check failed (HTTP ${connection.responseCode}).")
            parser.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: UpdateException) { throw e }
        catch (e: IOException) { throw UpdateException("Could not reach GitHub. Check your connection.", e) }
        finally { connection.disconnect() }
    }

    override suspend fun download(asset: ReleaseAsset, destination: File): DownloadedApk {
        destination.delete()
        try {
            var current = asset.url
            repeat(6) { redirect ->
                if (!UpdateUrlPolicy.isAllowed(current)) throw UpdateException("GitHub returned an unsafe download location.")
                val connection = connectionFactory(current)
                try {
                    connection.instanceFollowRedirects = false
                    configure(connection, "application/vnd.android.package-archive")
                    when (val status = connection.responseCode) {
                        in 200..299 -> {
                            validateContentLength(connection)
                            destination.parentFile?.mkdirs()
                            val bytesWritten = connection.inputStream.use { input ->
                                destination.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > maxDownloadBytes) {
                                            throw UpdateException("Downloaded APK exceeds the size limit.")
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                    total
                                }
                            }
                            if (bytesWritten == 0L) throw UpdateException("Downloaded APK was empty.")
                            if (!ApkStructureValidator.isValid(destination)) {
                                throw UpdateException("Downloaded file is not a valid APK.")
                            }
                            if (!DigestVerifier.matches(destination, asset.sha256)) {
                                throw UpdateException("Downloaded APK failed SHA-256 verification.")
                            }
                            return DownloadedApk(
                                file = destination,
                                integrity = if (asset.sha256 == null) {
                                    ApkIntegrity.STRUCTURE_VALIDATED
                                } else {
                                    ApkIntegrity.SHA256_VERIFIED
                                },
                            )
                        }
                        in 300..399 -> current = connection.getHeaderField("Location")
                            ?: throw UpdateException("GitHub redirect was missing a location.")
                        else -> throw UpdateException("APK download failed (HTTP $status).")
                    }
                } catch (e: UpdateException) { throw e }
                catch (e: IOException) { throw UpdateException("APK download failed. Check your connection.", e) }
                finally { connection.disconnect() }
                if (redirect == 5) throw UpdateException("Too many download redirects.")
            }
            throw UpdateException("APK download failed.")
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    private fun validateContentLength(connection: HttpURLConnection) {
        val rawLength = connection.getHeaderField("Content-Length")
        val declaredLength = if (rawLength != null) {
            val parsed = rawLength.toLongOrNull()
                ?: throw UpdateException("GitHub returned an invalid APK size.")
            if (parsed <= 0L) throw UpdateException("GitHub returned an invalid APK size.")
            parsed
        } else {
            connection.contentLengthLong
        }
        if (declaredLength < -1L) {
            throw UpdateException("GitHub returned an invalid APK size.")
        }
        if (declaredLength > maxDownloadBytes) {
            throw UpdateException("Downloaded APK exceeds the size limit.")
        }
    }

    private fun configure(connection: HttpURLConnection, accept: String) {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("User-Agent", "GymBro-Android")
    }

    companion object {
        const val LATEST_RELEASE_ENDPOINT = "https://api.github.com/repos/Vitorhpx/gymbro-native/releases/latest"
        const val MAX_APK_BYTES = 100L * 1024 * 1024
    }
}
