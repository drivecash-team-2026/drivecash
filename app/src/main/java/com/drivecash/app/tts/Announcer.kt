package com.drivecash.app.tts

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 주행 중 시선 이동 없이 보너스를 알려주는 음성 안내. (기획서의 TTS SDK 활용)
 *
 * 호출 순서 주의: addEventListener 는 initialize 이후에 불러야 한다. (docs/DEVELOPMENT.md V-08)
 * 에뮬레이터를 -no-audio 로 띄우면 재생 이벤트가 오지 않는다.
 */
class Announcer(private val context: Context) {

    private var engine: TextToSpeech? = null
    @Volatile private var ready = false

    // 안내가 겹치면 앞 안내가 잘리므로 한 문장씩 순서대로 재생한다. 모든 접근은 메인 스레드에서 한다.
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var speaking = false
    private val watchdog = Runnable {
        Log.w(TAG, "[TTS] 완료 이벤트가 ${WATCHDOG_MS}ms 안에 오지 않아 다음 안내로 넘어갑니다")
        finishCurrent()
    }

    fun start() {
        if (engine != null) return
        try {
            val e = TextToSpeech(context, Mode.ON_DEVICE)
            engine = e
            e.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    ready = true
                    Log.i(TAG, "[TTS] 초기화 성공")
                    e.addEventListener(object : EventListener {
                        override fun onStart() {
                            Log.i(TAG, "[TTS] 재생 시작")
                        }

                        override fun onReady() = Unit

                        override fun onDone() {
                            Log.i(TAG, "[TTS] 재생 완료")
                            main.post { finishCurrent() }
                        }

                        override fun onError(errorType: ErrorType) {
                            Log.w(TAG, "[TTS] 오류 $errorType")
                            main.post { finishCurrent() }
                        }
                        override fun onStop() {
                            main.post { finishCurrent() }
                        }
                        override fun onUpdatedRms(rms: Double) = Unit
                    })
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "[TTS] 초기화 실패 $errorType")
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "[TTS] 시작 실패: $t")
        }
    }

    fun say(text: String) {
        Log.i(TAG, "[TTS] 안내: $text (ready=$ready)")
        if (!ready) return
        main.post {
            queue.addLast(text)
            if (!speaking) playNext()
        }
    }

    private fun playNext() {
        val text = queue.removeFirstOrNull() ?: return
        speaking = true
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
        try {
            // 두 번째 인자는 isFinal 이다. 한 문장씩 끝나는 안내이므로 true 여야 onDone 이 온다.
            // false 이면 뒤에 문장이 이어지는 스트림으로 취급되어 onDone 이 오지 않는다. (V-08, 2026-11-10 에뮬레이터 실측)
            engine?.speak(text, true)
        } catch (t: Throwable) {
            Log.w(TAG, "[TTS] speak 실패: $t")
            finishCurrent()
        }
    }

    private fun finishCurrent() {
        if (!speaking) return
        speaking = false
        main.removeCallbacks(watchdog)
        playNext()
    }

    fun stop() {
        ready = false
        main.removeCallbacks(watchdog)
        queue.clear()
        speaking = false
        runCatching { engine?.release() }
        engine = null
    }

    companion object {
        private const val TAG = "DriveCash"
        private const val WATCHDOG_MS = 12_000L
    }
}
