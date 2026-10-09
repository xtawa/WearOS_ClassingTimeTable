package com.xtawa.classingtime.screen

import de.sciss.jump3r.mp3.*
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.Closeable
import java.io.OutputStream

/** Pure Java LAME encoder: mono 16 kHz/32 kbps, with no native ABI or desktop audio dependency. */
internal class VoiceMp3Encoder(private val output: OutputStream) : Closeable {
    private val lame = Lame()
    private val flags: LameGlobalFlags
    private val encoded = ByteArray(16384)
    private val samples = IntArray(4096)
    private var finished = false
    init {
        val ga = GainAnalysis(); val bs = BitStream(); val presets = Presets()
        val qpv = QuantizePVT(); val quantize = Quantize(); val vbr = VBRTag()
        val version = Version(); val id3 = ID3Tag(); val reservoir = Reservoir(); val takehiro = Takehiro()
        val mpg = MPGLib(); val intf = Interface(); val common = Common()
        lame.setModules(ga, bs, presets, qpv, quantize, vbr, version, id3, mpg)
        bs.setModules(ga, mpg, version, vbr); id3.setModules(bs, version); presets.setModules(lame)
        quantize.setModules(bs, reservoir, qpv, takehiro); qpv.setModules(takehiro, reservoir, lame.enc.psy)
        reservoir.setModules(bs); takehiro.setModules(qpv); vbr.setModules(lame, bs, version)
        mpg.setModules(intf, common); intf.setModules(vbr, common)
        flags = lame.lame_init().apply {
            num_channels = 1; in_samplerate = 16000; out_samplerate = 16000
            mode = MPEGMode.MONO; brate = 32; quality = 5; VBR = VbrMode.vbr_off
            bWriteVbrTag = false; write_id3tag_automatic = false; findReplayGain = false
        }
        id3.id3tag_init(flags)
        check(lame.lame_init_params(flags) >= 0) { "MP3 encoder initialization failed" }
    }
    fun write(pcm: ShortArray, count: Int) {
        check(!finished); require(count in 0..minOf(pcm.size, samples.size))
        for (i in 0 until count) samples[i] = pcm[i].toInt() shl 16
        val n = lame.lame_encode_buffer_int(flags, samples, samples, count, encoded, 0, encoded.size)
        check(n >= 0) { "MP3 encoding failed" }; if (n > 0) output.write(encoded, 0, n)
    }
    override fun close() {
        if (finished) return
        finished = true
        try { val n = lame.lame_encode_flush(flags, encoded, 0, encoded.size); check(n >= 0); if (n > 0) output.write(encoded, 0, n) }
        finally { lame.lame_close(flags) }
    }
}
