package com.example.helloworld

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface

class DiceWebInterface(
    private val callback: DiceCallback
) {
    interface DiceCallback {
        fun onDiceReady()
        fun onRollComplete(values: List<Int>)
    }

    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        Log.d("DiceWebInterface", "onReady called from JS!")
        handler.post { callback.onDiceReady() }
    }

    @JavascriptInterface
    fun onRollComplete(resultsJson: String) {
        try {
            val values = DiceResultParser.parse(resultsJson)
            handler.post { callback.onRollComplete(values) }
        } catch (e: Exception) {
            Log.e("DiceWebInterface", "Parse error: ${e.message}", e)
        }
    }
}
