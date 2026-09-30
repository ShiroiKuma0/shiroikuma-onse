package shiroikuma.onse.render

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 16-bit mono PCM → OGG/Opus, on the device's own codec: MediaCodec's Opus encoder feeding
 * MediaMuxer's OGG container (both API 29+). The file is written as `<target>.part` and renamed
 * only when complete, so a reader never sees a half file.
 */
object OggOpusWriter {

    fun isAvailable(): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_OPUS, true) }
        }

    fun write(pcm: ByteArray, sampleRate: Int, bitrateKbps: Int, target: File) {
        target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("cannot create ${it.path}") }
        val part = File(target.path + ".part")
        part.delete()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps.coerceIn(6, 256) * 1000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        var muxer: MediaMuxer? = null
        var ok = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(part.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            var track = -1
            var muxing = false
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            val bytesPerSecond = sampleRate * 2L
            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf: ByteBuffer = codec.getInputBuffer(inIndex)!!
                        buf.clear()
                        val n = minOf(buf.remaining() and 1.inv(), pcm.size - offset)
                        val ptsUs = offset * 1_000_000L / bytesPerSecond
                        if (n <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(pcm, offset, n)
                            offset += n
                            codec.queueInputBuffer(inIndex, 0, n, ptsUs, 0)
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    outIndex >= 0 -> {
                        val out = codec.getOutputBuffer(outIndex)!!
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!config && info.size > 0 && muxing) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            muxer.writeSampleData(track, out, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            muxer.stop()
            ok = true
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { muxer?.release() }
            if (!ok) part.delete()
        }
        if (!part.renameTo(target)) {
            // renameTo refuses to replace on some filesystems — replace explicitly.
            target.delete()
            if (!part.renameTo(target)) {
                part.delete()
                throw IOException("could not move ${part.name} into place")
            }
        }
    }

    /** A plain RIFF WAV, for callers asking `format=wav` (or devices without an Opus encoder). */
    fun writeWav(wav: ByteArray, target: File) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        part.writeBytes(wav)
        target.delete()
        if (!part.renameTo(target)) {
            part.delete()
            throw IOException("could not move ${part.name} into place")
        }
    }
}
