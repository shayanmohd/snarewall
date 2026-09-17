package com.mohdshayan.snarewall.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.mohdshayan.snarewall.R

/** CC0 effects from Kenney Impact Sounds and Interface Sounds, played through one SoundPool. */
class Sfx(context: Context) {

    enum class Sound(val res: Int) {
        PLACE(R.raw.sfx_place), WALL(R.raw.sfx_wall), TAP(R.raw.sfx_tap), INVALID(R.raw.sfx_invalid),
        KILL(R.raw.sfx_kill), LEAK(R.raw.sfx_leak), COMBO(R.raw.sfx_combo), SPIKE(R.raw.sfx_spike),
        FIRE(R.raw.sfx_fire), DEADFALL(R.raw.sfx_deadfall), HAMMER(R.raw.sfx_hammer), DART(R.raw.sfx_dart),
        BREAK(R.raw.sfx_break), WAVE(R.raw.sfx_wave), WON(R.raw.sfx_won), LOST(R.raw.sfx_lost),
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = IntArray(Sound.entries.size)
    private val lastPlayed = LongArray(Sound.entries.size)

    @Volatile
    var volume = 0.7f

    init {
        Sound.entries.forEach { ids[it.ordinal] = pool.load(context, it.res, 1) }
    }

    /** Plays [s], at most once per [MIN_GAP_MS] so a 3x wave does not become noise. */
    fun play(s: Sound) {
        if (volume <= 0.01f) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayed[s.ordinal] < MIN_GAP_MS) return
        lastPlayed[s.ordinal] = now
        pool.play(ids[s.ordinal], volume, volume, 1, 0, 1f)
    }

    companion object {
        const val MIN_GAP_MS = 70L
    }
}
