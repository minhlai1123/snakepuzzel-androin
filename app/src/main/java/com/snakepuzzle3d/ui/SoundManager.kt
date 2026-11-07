package com.snakepuzzle3d.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object SoundManager {
    private const val TAG = "SoundManager"

    var isSoundEnabled = true
    var isMusicEnabled = true
    var isHapticEnabled = true

    private var soundPool: SoundPool? = null
    private var soundIdMove: Int = 0
    private var soundIdEscape: Int = 0
    private var soundIdVictory: Int = 0
    private var soundIdBlocked: Int = 0
    private var soundIdTap: Int = 0
    private var soundIdTimeUp: Int = 0
    private var isPoolLoaded = false

    // Direct PCM buffers for instantaneous playback fallback
    private var pcmMove: ByteArray? = null
    private var pcmEscape: ByteArray? = null
    private var pcmVictory: ByteArray? = null
    private var pcmBlocked: ByteArray? = null
    private var pcmTap: ByteArray? = null
    private var pcmTimeUp: ByteArray? = null

    // Background Music MediaPlayer
    private var mediaPlayerBgm: MediaPlayer? = null
    private var bgmFile: File? = null

    fun initialize(context: Context) {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(audioAttributes)
                .build()

            pool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) {
                    isPoolLoaded = true
                    Log.d(TAG, "SoundPool sample loaded successfully")
                }
            }
            soundPool = pool

            // Generate PCM audio samples in memory
            pcmMove = generateMovePopWav()
            pcmEscape = generateEscapeChimeWav()
            pcmVictory = generateVictoryFanfareWav()
            pcmBlocked = generateBlockedThudWav()
            pcmTap = generateTapClickWav()
            pcmTimeUp = generateTimeUpWav()

            val cache = context.cacheDir
            soundIdMove = pool.load(writeTempWav(cache, "snd_move.wav", pcmMove!!).absolutePath, 1)
            soundIdEscape = pool.load(writeTempWav(cache, "snd_escape.wav", pcmEscape!!).absolutePath, 1)
            soundIdVictory = pool.load(writeTempWav(cache, "snd_victory.wav", pcmVictory!!).absolutePath, 1)
            soundIdBlocked = pool.load(writeTempWav(cache, "snd_blocked.wav", pcmBlocked!!).absolutePath, 1)
            soundIdTap = pool.load(writeTempWav(cache, "snd_tap.wav", pcmTap!!).absolutePath, 1)
            soundIdTimeUp = pool.load(writeTempWav(cache, "snd_timeup.wav", pcmTimeUp!!).absolutePath, 1)

            // Generate and prepare Background Music
            val bgmWav = generateBgmWav()
            bgmFile = writeTempWav(cache, "bgm_loop.wav", bgmWav)
            setupBgm(context)

            Log.d(TAG, "SoundManager synthesized and registered SFX and BGM successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing SoundManager: ${t.message}", t)
        }
    }

    private fun setupBgm(context: Context) {
        val file = bgmFile ?: return
        try {
            mediaPlayerBgm?.release()
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.isLooping = true
            mp.setVolume(0.32f, 0.32f)
            mp.prepare()
            mediaPlayerBgm = mp

            if (isMusicEnabled) {
                mp.start()
            }
            Log.d(TAG, "Background Music initialized and looping")
        } catch (t: Throwable) {
            Log.w(TAG, "Error setting up BGM MediaPlayer: ${t.message}")
        }
    }

    fun startBgm(context: Context? = null) {
        if (!isMusicEnabled) return
        try {
            val mp = mediaPlayerBgm
            if (mp != null) {
                if (!mp.isPlaying) {
                    mp.start()
                }
            } else if (context != null) {
                setupBgm(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "startBgm: ${t.message}")
        }
    }

    fun pauseBgm() {
        try {
            if (mediaPlayerBgm?.isPlaying == true) {
                mediaPlayerBgm?.pause()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "pauseBgm: ${t.message}")
        }
    }

    fun resumeBgm() {
        if (isMusicEnabled) {
            try {
                if (mediaPlayerBgm != null && mediaPlayerBgm?.isPlaying == false) {
                    mediaPlayerBgm?.start()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "resumeBgm: ${t.message}")
            }
        }
    }

    fun toggleMusic(context: Context? = null): Boolean {
        isMusicEnabled = !isMusicEnabled
        if (isMusicEnabled) {
            startBgm(context)
        } else {
            pauseBgm()
        }
        return isMusicEnabled
    }

    private fun writeTempWav(cacheDir: File, name: String, data: ByteArray): File {
        val file = File(cacheDir, name)
        FileOutputStream(file).use { it.write(data) }
        return file
    }

    private fun playDirectTrack(wavBytes: ByteArray?) {
        if (wavBytes == null || wavBytes.size <= 44) return
        try {
            val sampleRate = 44100
            val pcmData = wavBytes.copyOfRange(44, wavBytes.size)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmData.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcmData, 0, pcmData.size)
            track.play()
            track.setNotificationMarkerPosition(pcmData.size / 2)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) {
                    try {
                        t.stop()
                        t.release()
                    } catch (ignored: Throwable) {}
                }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Direct AudioTrack playback fallback: ${t.message}")
        }
    }

    fun playMove(view: View? = null) {
        if (isHapticEnabled && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (!isSoundEnabled) return
        val pool = soundPool
        if (pool != null && soundIdMove != 0) {
            pool.play(soundIdMove, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            playDirectTrack(pcmMove)
        }
    }

    fun playEscape(view: View? = null) {
        if (isHapticEnabled && view != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        if (!isSoundEnabled) return
        val pool = soundPool
        if (pool != null && soundIdEscape != 0) {
            pool.play(soundIdEscape, 1.0f, 1.0f, 2, 0, 1.0f)
        } else {
            playDirectTrack(pcmEscape)
        }
    }

    fun playVictory(scope: CoroutineScope, view: View? = null) {
        if (isHapticEnabled && view != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
        }
        if (!isSoundEnabled) return
        scope.launch(Dispatchers.Default) {
            val pool = soundPool
            if (pool != null && soundIdVictory != 0) {
                pool.play(soundIdVictory, 1.0f, 1.0f, 3, 0, 1.0f)
            } else {
                playDirectTrack(pcmVictory)
            }
        }
    }

    fun playBlocked(view: View? = null) {
        if (isHapticEnabled && view != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        if (!isSoundEnabled) return
        val pool = soundPool
        if (pool != null && soundIdBlocked != 0) {
            pool.play(soundIdBlocked, 0.85f, 0.85f, 1, 0, 1.0f)
        } else {
            playDirectTrack(pcmBlocked)
        }
    }

    fun playTap(view: View? = null) {
        if (isHapticEnabled && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (!isSoundEnabled) return
        val pool = soundPool
        if (pool != null && soundIdTap != 0) {
            pool.play(soundIdTap, 0.7f, 0.7f, 1, 0, 1.0f)
        } else {
            playDirectTrack(pcmTap)
        }
    }

    fun playTimeUp(view: View? = null) {
        if (isHapticEnabled && view != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
        }
        if (!isSoundEnabled) return
        val pool = soundPool
        if (pool != null && soundIdTimeUp != 0) {
            pool.play(soundIdTimeUp, 1.0f, 1.0f, 4, 0, 1.0f)
        } else {
            playDirectTrack(pcmTimeUp)
        }
    }

    fun playTick(view: View? = null) {
        if (isHapticEnabled && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (!isSoundEnabled) return
        val pool = soundPool
        if (pool != null && soundIdTap != 0) {
            pool.play(soundIdTap, 0.45f, 0.45f, 1, 0, 1.4f)
        }
    }

    // =========================================================================
    // HIGH-QUALITY CASUAL SYNTHESIZED WAV AUDIO BUFFERS (16-bit Mono)
    // =========================================================================

    private fun buildWav(samples: FloatArray, sampleRate: Int = 44100): ByteArray {
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
        buffer.putInt(totalSize)
        buffer.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())

        // fmt chunk
        buffer.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size
        buffer.putShort(1) // AudioFormat (1 = PCM)
        buffer.putShort(1) // NumChannels (1 = Mono)
        buffer.putInt(sampleRate) // SampleRate
        buffer.putInt(sampleRate * 2) // ByteRate
        buffer.putShort(2) // BlockAlign
        buffer.putShort(16) // BitsPerSample

        // data chunk
        buffer.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
        buffer.putInt(dataSize)

        for (s in samples) {
            val clamped = s.coerceIn(-1.0f, 1.0f)
            val pcm = (clamped * 32767.0f).toInt().toShort()
            buffer.putShort(pcm)
        }

        return buffer.array()
    }

    /**
     * Move sound: Delightful cartoon bubble pop (420 Hz -> 940 Hz with exponential decay).
     */
    private fun generateMovePopWav(): ByteArray {
        val sr = 44100
        val dur = 0.080f
        val count = (dur * sr).toInt()
        val samples = FloatArray(count)

        for (i in 0 until count) {
            val t = i.toFloat() / sr.toFloat()
            val progress = t / dur
            val freq = 420.0 + 520.0 * progress
            val env = exp(-progress * 5.0)
            val wave = sin(2.0 * Math.PI * freq * t).toFloat()
            samples[i] = (wave * env).toFloat()
        }
        return buildWav(samples, sr)
    }

    /**
     * Escape sound: Joyful ascending melodic chime (587 Hz -> 880 Hz / D5 to A5).
     */
    private fun generateEscapeChimeWav(): ByteArray {
        val sr = 44100
        val dur = 0.250f
        val count = (dur * sr).toInt()
        val samples = FloatArray(count)

        for (i in 0 until count) {
            val t = i.toFloat() / sr.toFloat()
            val wave: Double
            val env: Double
            if (t < 0.070f) {
                val p = t / 0.070f
                wave = sin(2.0 * Math.PI * 587.33 * t)
                env = 1.0 - (1.0 - p) * 0.2
            } else {
                val t2 = t - 0.070f
                wave = sin(2.0 * Math.PI * 880.00 * t2) + 0.3 * sin(2.0 * Math.PI * 1760.00 * t2)
                env = exp(-t2 * 6.0)
            }
            samples[i] = (wave * env * 0.85).toFloat()
        }
        return buildWav(samples, sr)
    }

    /**
     * Victory sound: 4-note celebratory fanfare (C5, E5, G5, C6).
     */
    private fun generateVictoryFanfareWav(): ByteArray {
        val sr = 44100
        val dur = 0.700f
        val count = (dur * sr).toInt()
        val samples = FloatArray(count)

        val notes = listOf(
            Triple(0.00f, 0.12f, 523.25), // C5
            Triple(0.12f, 0.12f, 659.25), // E5
            Triple(0.24f, 0.14f, 783.99), // G5
            Triple(0.38f, 0.32f, 1046.50) // C6
        )

        for (i in 0 until count) {
            val t = i.toFloat() / sr.toFloat()
            var currentSample = 0.0
            for (note in notes) {
                if (t >= note.first && t < (note.first + note.second)) {
                    val localT = t - note.first
                    val env = exp(-localT * 3.5)
                    val wave = sin(2.0 * Math.PI * note.third * localT) + 0.25 * sin(2.0 * Math.PI * note.third * 2.0 * localT)
                    currentSample += wave * env
                }
            }
            samples[i] = (currentSample * 0.75).coerceIn(-1.0, 1.0).toFloat()
        }
        return buildWav(samples, sr)
    }

    /**
     * Blocked sound: Low rubbery thud (140 Hz down to 60 Hz pitch drop).
     */
    private fun generateBlockedThudWav(): ByteArray {
        val sr = 44100
        val dur = 0.120f
        val count = (dur * sr).toInt()
        val samples = FloatArray(count)

        for (i in 0 until count) {
            val t = i.toFloat() / sr.toFloat()
            val progress = t / dur
            val freq = 140.0 - 70.0 * progress
            val env = exp(-progress * 7.0)
            val wave = sin(2.0 * Math.PI * freq * t).toFloat()
            samples[i] = (wave * env * 0.8).toFloat()
        }
        return buildWav(samples, sr)
    }

    /**
     * Tap click: Crisp 1200 Hz click.
     */
    private fun generateTapClickWav(): ByteArray {
        val sr = 44100
        val dur = 0.025f
        val count = (dur * sr).toInt()
        val samples = FloatArray(count)

        for (i in 0 until count) {
            val t = i.toFloat() / sr.toFloat()
            val progress = t / dur
            val env = exp(-progress * 16.0)
            val wave = sin(2.0 * Math.PI * 1200.0 * t).toFloat()
            samples[i] = (wave * env * 0.5).toFloat()
        }
        return buildWav(samples, sr)
    }

    /**
     * Time-up sound: Descending buzzer chime (380 Hz -> 220 Hz down-slide).
     */
    private fun generateTimeUpWav(): ByteArray {
        val sr = 44100
        val dur = 0.400f
        val count = (dur * sr).toInt()
        val samples = FloatArray(count)

        for (i in 0 until count) {
            val t = i.toFloat() / sr.toFloat()
            val progress = t / dur
            val freq = 380.0 - 160.0 * progress
            val env = exp(-progress * 3.5)
            val wave = sin(2.0 * Math.PI * freq * t) + 0.35 * sin(4.0 * Math.PI * freq * t)
            samples[i] = (wave * env * 0.7).coerceIn(-1.0, 1.0).toFloat()
        }
        return buildWav(samples, sr)
    }

    /**
     * Background Music: Seamless 8.0-second looping casual puzzle melody (22,050 Hz).
     * Melodic acoustic marimba chords (C -> G -> Am -> F) with warm sub-bass.
     */
    private fun generateBgmWav(): ByteArray {
        val sr = 22050
        val dur = 8.0f
        val count = (dur * sr).toInt()
        val samples = FloatArray(count)

        val notes = listOf(
            // Bar 1: C major
            Pair(0.00, 261.63), Pair(0.25, 329.63), Pair(0.50, 392.00), Pair(0.75, 523.25),
            Pair(1.00, 392.00), Pair(1.25, 329.63), Pair(1.50, 392.00), Pair(1.75, 329.63),
            // Bar 2: G major
            Pair(2.00, 246.94), Pair(2.25, 293.66), Pair(2.50, 392.00), Pair(2.75, 493.88),
            Pair(3.00, 392.00), Pair(3.25, 293.66), Pair(3.50, 392.00), Pair(3.75, 293.66),
            // Bar 3: A minor
            Pair(4.00, 220.00), Pair(4.25, 261.63), Pair(4.50, 329.63), Pair(4.75, 440.00),
            Pair(5.00, 329.63), Pair(5.25, 261.63), Pair(5.50, 329.63), Pair(5.75, 261.63),
            // Bar 4: F major
            Pair(6.00, 174.61), Pair(6.25, 220.00), Pair(6.50, 261.63), Pair(6.75, 349.23),
            Pair(7.00, 329.63), Pair(7.25, 293.66), Pair(7.50, 261.63), Pair(7.75, 293.66)
        )

        val bassNotes = listOf(
            Triple(0.0, 2.0, 130.81), // C3
            Triple(2.0, 2.0, 98.00),  // G2
            Triple(4.0, 2.0, 110.00), // A2
            Triple(6.0, 2.0, 87.31)   // F2
        )

        for (i in 0 until count) {
            val t = i.toDouble() / sr.toDouble()
            var sum = 0.0

            for (n in notes) {
                val start = n.first
                val freq = n.second
                if (t >= start && t < (start + 0.50)) {
                    val lt = t - start
                    val env = exp(-lt * 4.5) * (1.0 - exp(-lt * 80.0))
                    val wave = sin(2.0 * Math.PI * freq * lt) + 0.35 * sin(4.0 * Math.PI * freq * lt) + 0.15 * sin(6.0 * Math.PI * freq * lt)
                    sum += wave * env * 0.28
                }
            }

            for (b in bassNotes) {
                val start = b.first
                val bdur = b.second
                val freq = b.third
                if (t >= start && t < (start + bdur)) {
                    val lt = t - start
                    val env = exp(-lt * 1.5) * (1.0 - exp(-lt * 40.0))
                    val wave = sin(2.0 * Math.PI * freq * lt) + 0.3 * sin(4.0 * Math.PI * freq * lt)
                    sum += wave * env * 0.22
                }
            }

            samples[i] = sum.coerceIn(-0.95, 0.95).toFloat()
        }

        return buildWav(samples, sr)
    }
}
