package com.xtawa.classingtime.screen

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AskAiCameraFileTest {
    @Test fun cameraOutputIsWritableThroughTheConfiguredFileProvider() {
        // AndroidX 1.15 FileProvider.belongsToRoot appends '/' while Windows
        // canonical paths contain '\\'. Keep the real provider test in Linux CI.
        org.junit.Assume.assumeTrue("Android FileProvider requires POSIX paths", java.io.File.separatorChar == '/')
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photo = createAskAiCameraFile(context)
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
            assertEquals("content", uri.scheme)
            context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())) }
            assertEquals(3L, photo.length())
            assertTrue(uri.path.orEmpty().contains("ai_photos"))
            assertTrue(runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(context.cacheDir, "private-voice.mp3"))
            }.isFailure)
        } finally { photo.delete() }
    }
}
