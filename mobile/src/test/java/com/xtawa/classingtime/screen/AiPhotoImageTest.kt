package com.xtawa.classingtime.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiPhotoImageTest {
    @Test fun validImage_boundsDecodeNullDoesNotRejectPhoto() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("photo", ".png", context.cacheDir)
        try {
            Bitmap.createBitmap(2800, 1000, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.WHITE)
                file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
                recycle()
            }
            val jpeg = prepareTimetablePhoto(context, Uri.fromFile(file))
            val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            assertNotNull(decoded)
            assertTrue(decoded.width <= 2048)
            assertTrue(jpeg.size <= 2_500_000)
            assertEquals(0xff, jpeg[0].toInt() and 255)
            decoded.recycle()
        } finally { file.delete() }
    }
    @Test fun corruptedImage_isRejectedBeforeUpload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("photo", ".jpg", context.cacheDir)
        try {
            file.writeText("not an image")
            assertTrue(runCatching { prepareTimetablePhoto(context, Uri.fromFile(file)) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun cloudRecording_wavHeaderDescribes16kMonoPcm() {
        val header = java.nio.ByteBuffer.wrap(wavHeader(32000)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(32036, header.getInt(4))
        assertEquals(16000, header.getInt(24))
        assertEquals(32000, header.getInt(40))
    }
}
