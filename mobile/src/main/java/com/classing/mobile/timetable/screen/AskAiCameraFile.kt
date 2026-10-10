package com.xtawa.classingtime.screen

import android.content.Context
import java.io.File
import java.io.IOException

// update_file_paths.xml exposes only this photo directory and update downloads.
internal fun createAskAiCameraFile(context: Context): File {
    val directory = File(context.cacheDir, "ai_photos")
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create photo cache")
    return File.createTempFile("ask-camera-", ".jpg", directory)
}
