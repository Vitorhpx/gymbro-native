package com.gymbro.local

import com.gymbro.local.update.ApkIntegrity
import com.gymbro.local.update.AppRelease
import com.gymbro.local.update.ReleaseAsset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateUiCopyTest {
    @Test fun `digest-free update copy never claims verification`() {
        val release = release(sha256 = null)
        val downloaded = UpdateUiState.Downloaded(File("update.apk"), ApkIntegrity.STRUCTURE_VALIDATED)

        assertFalse(UpdateUiCopy.downloadButton(release).contains("verified", ignoreCase = true))
        assertFalse(UpdateUiCopy.downloadedMessage(downloaded).contains("verified", ignoreCase = true))
        assertTrue(UpdateUiCopy.downloadedMessage(downloaded).contains("structurally validated", ignoreCase = true))
    }

    @Test fun `matching digest copy explicitly says SHA-256 verified`() {
        val downloaded = UpdateUiState.Downloaded(File("update.apk"), ApkIntegrity.SHA256_VERIFIED)

        assertTrue(UpdateUiCopy.downloadedMessage(downloaded).contains("SHA-256 verified"))
    }

    private fun release(sha256: String?) = AppRelease(
        tagName = "v0.3.0",
        apk = ReleaseAsset("gymbro.apk", "https://github.com/Vitorhpx/gymbro-native/releases/download/v0.3.0/gymbro.apk", sha256),
    )
}