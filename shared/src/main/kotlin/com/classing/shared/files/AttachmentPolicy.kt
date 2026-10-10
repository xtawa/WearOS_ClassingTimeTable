package com.classing.shared.files

/** Ordinary attachments never execute code. Voice input uses a separate transcription endpoint. */
object AttachmentPolicy {
    private val blockedExtensions = ("exe dll msi com bat cmd ps1 sh bash zsh app apk aab ipa dmg pkg deb rpm jar class wasm scr lnk desktop vbs vbe wsf hta jse js py pyc pyo rb pl php bin iso " +
        "mp3 wav m4a aac ogg oga opus flac aiff aif wma amr mid midi mp4 m4v mov mkv webm avi wmv flv mpeg mpg 3gp ts mts m2ts vob").split(' ').toSet()
    private val executableMimes = setOf("application/x-executable", "application/x-msdownload", "application/x-msdos-program",
        "application/vnd.microsoft.portable-executable", "application/x-mach-binary", "application/x-sh", "application/x-shellscript",
        "application/vnd.android.package-archive", "application/java-archive", "application/javascript", "text/javascript", "text/x-python")

    fun isAllowed(name: String, mime: String, header: ByteArray = byteArrayOf()): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        val type = mime.substringBefore(';').trim().lowercase()
        if (extension in blockedExtensions || type.startsWith("audio/") || type.startsWith("video/") || type in executableMimes) return false
        fun prefix(value: String) = header.size >= value.length && value.indices.all { header[it] == value[it].code.toByte() }
        if (prefix("MZ") || prefix("\u007fELF") || prefix("#!") || prefix("ID3") || prefix("fLaC") || prefix("OggS")) return false
        if (header.size >= 2 && (header[0].toInt() and 255) == 255 && (header[1].toInt() and 224) == 224) return false
        if (header.size >= 12) {
            val kind = header.copyOfRange(8, 12).toString(Charsets.US_ASCII)
            if (prefix("RIFF") && kind in setOf("WAVE", "AVI ")) return false
            if (header.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" && kind !in setOf("avif", "avis", "heic", "heix", "hevc", "hevx", "mif1", "msf1")) return false
        }
        if (header.size >= 4) {
            val magic = header.take(4).map { it.toInt() and 255 }
            if (magic in listOf(listOf(0xfe,0xed,0xfa,0xce), listOf(0xfe,0xed,0xfa,0xcf), listOf(0xce,0xfa,0xed,0xfe), listOf(0xcf,0xfa,0xed,0xfe), listOf(0xca,0xfe,0xba,0xbe), listOf(0x1a,0x45,0xdf,0xa3))) return false
        }
        return true
    }
}
