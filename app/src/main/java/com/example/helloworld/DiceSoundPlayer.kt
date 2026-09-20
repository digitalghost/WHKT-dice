package com.example.helloworld

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

class DiceSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var soundId = 0
    private var streamId = 0
    private var loaded = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
        }
        soundId = soundPool.load(context, R.raw.dice_roll, 1)
    }

    fun play() {
        if (!loaded) return
        if (streamId != 0) soundPool.stop(streamId)
        streamId = soundPool.play(
            soundId,
            0.72f,
            0.72f,
            1,
            0,
            Random.nextDouble(0.97, 1.04).toFloat()
        )
    }

    fun release() {
        soundPool.release()
        loaded = false
        streamId = 0
    }
}
