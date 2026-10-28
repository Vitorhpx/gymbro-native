package com.gymbro.local.update

import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppUpdateTest {
    private val fixture = """
      {"tag_name":"v0.3.0","name":"GymBro 0.3.0","assets":[
        {"name":"notes.txt","browser_download_url":"https://github.com/Vitorhpx/gymbro-native/releases/download/v0.3.0/notes.txt","digest":"sha256:00"},
        {"name":"gymbro-arm64.apk","browser_download_url":"https://github.com/Vitorhpx/gymbro-native/releases/download/v0.3.0/gymbro-arm64.apk","digest":"sha256:bb"},
        {"name":"gymbro-universal.apk","browser_download_url":"https://github.com/Vitorhpx/gymbro-native/releases/download/v0.3.0/gymbro-universal.apk","digest":"sha256:aa"}
      ]}
    """.trimIndent()

    @Test fun `semver detects only newer stable versions`() {
        assertTrue(SemVer.isNewer("v0.2.1", "0.2.0"))
        assertTrue(SemVer.isNewer("1.0.0", "0.9.99"))
        assertFalse(SemVer.isNewer("0.2.0", "0.2.0"))
        assertFalse(SemVer.isNewer("0.1.9", "0.2.0"))
        assertFalse(SemVer.isNewer("not-a-version", "0.2.0"))
    }

    @Test fun `release parser selects universal APK deterministically`() {
        val release = ReleaseParser(Json { ignoreUnknownKeys = true }).parse(fixture)
        assertEquals("v0.3.0", release.tagName)
        assertEquals("gymbro-universal.apk", release.apk?.name)
        assertEquals("aa", release.apk?.sha256)
    }

    @Test fun `release without APK is not installable`() {
        val release = ReleaseParser(Json { ignoreUnknownKeys = true }).parse("""{"tag_name":"v1.0.0","assets":[]}""")
        assertNull(release.apk)
    }

    @Test fun `download allowlist accepts only HTTPS GitHub release locations`() {
        assertTrue(UpdateUrlPolicy.isAllowed("https://github.com/Vitorhpx/gymbro-native/releases/download/v0.3.0/gymbro.apk"))
        assertTrue(UpdateUrlPolicy.isAllowed("https://release-assets.githubusercontent.com/github-production-release-asset/123/file?x=1"))
        assertFalse(UpdateUrlPolicy.isAllowed("http://github.com/Vitorhpx/gymbro-native/releases/download/v0.3.0/a.apk"))
        assertFalse(UpdateUrlPolicy.isAllowed("https://evil.example/a.apk"))
        assertFalse(UpdateUrlPolicy.isAllowed("https://github.com/other/repo/releases/download/v1/a.apk"))
    }

    @Test fun `digest verifier accepts matching SHA256 and rejects mismatch`() {
        val file = File.createTempFile("gymbro-update", ".apk").apply { writeText("apk bytes") }
        try {
            assertTrue(DigestVerifier.matches(file, "9379cfb95416438572c33e2c2e03f1fdbdb10e4668cfbdb7bbd0e3049463ac33"))
            assertFalse(DigestVerifier.matches(file, "00".repeat(32)))
            assertTrue(DigestVerifier.matches(file, null))
        } finally { file.delete() }
    }

    @Test fun `download rejects declared over-limit size and deletes destination`() = runBlocking {
        val destination = File.createTempFile("gymbro-update", ".apk").apply { writeText("old partial") }
        val service = serviceFor(byteArrayOf(1), contentLength = 101L * 1024 * 1024)

        val failure = runCatching { service.download(asset(), destination) }.exceptionOrNull()

        assertTrue(failure is UpdateException)
        assertFalse(destination.exists())
    }

    @Test fun `download rejects nonpositive Content-Length when header is present`() = runBlocking {
        val destination = File.createTempFile("gymbro-update", ".apk")
        val service = serviceFor(validApk(), contentLength = -1, contentLengthHeader = "-1")

        val failure = runCatching { service.download(asset(), destination) }.exceptionOrNull()

        assertTrue(failure is UpdateException)
        assertFalse(destination.exists())
    }

    @Test fun `download enforces streaming cap when server omits length`() = runBlocking {
        val destination = File.createTempFile("gymbro-update", ".apk")
        val service = serviceFor(validApk(), contentLength = -1, maxDownloadBytes = 16)

        val failure = runCatching { service.download(asset(), destination) }.exceptionOrNull()

        assertTrue(failure is UpdateException)
        assertFalse(destination.exists())
    }

    @Test fun `download rejects empty and non-APK bodies`() = runBlocking {
        listOf(byteArrayOf(), "not an apk".toByteArray()).forEach { body ->
            val destination = File.createTempFile("gymbro-update", ".apk")

            val failure = runCatching { serviceFor(body).download(asset(), destination) }.exceptionOrNull()

            assertTrue(failure is UpdateException)
            assertFalse(destination.exists())
        }
    }

    @Test fun `download accepts structurally valid APK without digest`() = runBlocking {
        val destination = File.createTempFile("gymbro-update", ".apk")
        val apk = validApk()

        val downloaded = serviceFor(apk).download(asset(), destination)

        assertEquals(destination, downloaded.file)
        assertEquals(ApkIntegrity.STRUCTURE_VALIDATED, downloaded.integrity)
        assertTrue(destination.exists())
    }

    @Test fun `download reports SHA-256 verified only when supplied digest matches`() = runBlocking {
        val destination = File.createTempFile("gymbro-update", ".apk")
        val apk = validApk()
        val digest = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }

        val downloaded = serviceFor(apk).download(asset(sha256 = digest), destination)

        assertEquals(ApkIntegrity.SHA256_VERIFIED, downloaded.integrity)
    }

    @Test fun `download rejects digest mismatch and deletes APK`() = runBlocking {
        val destination = File.createTempFile("gymbro-update", ".apk")

        val failure = runCatching {
            serviceFor(validApk()).download(asset(sha256 = "00".repeat(32)), destination)
        }.exceptionOrNull()

        assertTrue(failure is UpdateException)
        assertFalse(destination.exists())
    }

    private fun asset(sha256: String? = null) = ReleaseAsset(
        name = "gymbro.apk",
        url = "https://github.com/Vitorhpx/gymbro-native/releases/download/v0.3.0/gymbro.apk",
        sha256 = sha256,
    )

    private fun serviceFor(
        body: ByteArray,
        contentLength: Long = body.size.toLong(),
        contentLengthHeader: String? = contentLength.takeIf { it >= 0 }?.toString(),
        maxDownloadBytes: Long = 100L * 1024 * 1024,
    ) = GitHubAppUpdateService(
        connectionFactory = { FakeConnection(body, contentLength, contentLengthHeader) },
        maxDownloadBytes = maxDownloadBytes,
    )

    private fun validApk(): ByteArray = java.io.ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("manifest".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write("dex".toByteArray())
            zip.closeEntry()
        }
        bytes.toByteArray()
    }

    private class FakeConnection(
        private val body: ByteArray,
        private val declaredLength: Long,
        private val contentLengthHeader: String?,
    ) : HttpURLConnection(URL("https://github.com")) {
        override fun getResponseCode() = HTTP_OK
        override fun getContentLengthLong() = declaredLength
        override fun getHeaderField(name: String?): String? =
            if (name.equals("Content-Length", ignoreCase = true)) contentLengthHeader else null
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun connect() = Unit
    }
}
