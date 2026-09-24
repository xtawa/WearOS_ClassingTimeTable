package com.xtawa.classingtime.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun prepareTimetablePhoto(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("Cannot read photo")
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid photo" }
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 2048) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Cannot decode photo")
    val rotation = resolver.openInputStream(uri)?.use { stream ->
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val bitmap = if (rotation == 0f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
        Matrix().apply { postRotate(rotation) }, true).also { decoded.recycle() }
    try {
        var quality = 88
        var bytes: ByteArray
        do {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            bytes = output.toByteArray()
            quality -= 12
        } while (bytes.size > 2_500_000 && quality >= 40)
        require(bytes.size <= 2_500_000) { "Photo is too large" }
        bytes
    } finally { bitmap.recycle() }
}
