package com.jarvis.mobile.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * جلسة صوت Jarvis — النقطة الوحيدة التي تنطق في التطبيق كله (Phase 7 ربط UI).
 *
 * الحواجز المعمارية:
 * 1. **مرة واحدة على مستوى العملية**: التحية تُنطق مرة واحدة فقط منذ بدء التطبيق —
 *    حاجز [AtomicBoolean] بنيوي: لا recomposition ولا إعادة تشغيل Activity ولا
 *    lifecycle يعيد نطقها أبداً. UI يستدعي [speakGreetingOnce] متى شاء.
 * 2. **لا Compose هنا**: الجلسة كائن عادي — لا يعرف recomposition — يُحتفظ به في
 *    Application (يعيش مع العملية كاملة، أطول من أي Activity).
 * 3. **سلسلة TTS إلزامياً**: كل نطق يمر عبر [TtsFallbackChain] (تطبيع + شخصية +
 *    ترتيب طبقات + fallback) — لا طبقة تُستدعى مباشرة من UI إطلاقاً.
 * 4. **آلة الحالة تُخبَر فعلياً**: SPEAKING عند بدء النطق، ثم RESET بعد انتهائه —
 *    والتحية لا تُنطق إلا إذا قَبِلت الآلة الانتقال (آمن ضد سباقات الآلة).
 * 5. **تنفيذ خارج Main thread**: التوليد العصبي ثقيل — يجري على منفذ أحادي خلفي،
 *    الطلبات المتتالية تُسلسل (speak واحد في وقت واحد — منفذ single-thread).
 */
class JarvisVoiceSession(
    private val chain: TtsFallbackChain,
    private val machine: JarvisMachineAdapter,
    private val voice: JarvisVoiceSpec = JarvisVoiceSpec.JARVIS,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "jarvis-voice").apply { isDaemon = true }
    },
    /** قابل للحقن للاختبارات — الافتراضي: لا شيء (لا Logs في الإنتاج حتى الآن). */
    private val onResult: (TtsResult) -> Unit = {},
) {
    /** محوّل آلة الحالة — يفصل الجلسة عن core للسهولة القابلة للاختبار. */
    interface JarvisMachineAdapter {
        /** true إن قَبِلت الآلة الانتقال إلى SPEAKING الآن. */
        fun beginSpeaking(): Boolean

        /** إعادة الآلة إلى وضعها الهادئ بعد انتهاء النطق. */
        fun doneSpeaking()
    }

    private val greeted = AtomicBoolean(false)

    /** الطبقة التي نطقت آخر مرة فعلياً — تُعرض في لوحة التشخيص (تحقق الجهاز الحقيقي). */
    private val _latestTier = MutableStateFlow("—")
    val latestTier: StateFlow<String> = _latestTier.asStateFlow()

    private fun record(result: TtsResult) {
        _latestTier.value = when (result) {
            is TtsResult.Success -> when (result.tier) {
                TtsTier.NEURAL -> "NEURAL (sherpa-onnx)"
                TtsTier.CLOUD -> "CLOUD"
                TtsTier.SYSTEM -> "SYSTEM (fallback)"
            }
            is TtsResult.NotSupported -> "غير مدعوم: ${result.reason}"
            is TtsResult.SafeFailure -> "فشل كل الطبقات: ${result.reasons.joinToString(" | ")}"
        }
        onResult(result)
    }

    /**
     * أول تحية عربية لـJarvis — تُنطق **مرة واحدة على مستوى العملية** فقط.
     * الاستدعاءات اللاحقة (recomposition، Activity جديدة، lifecycle) لا-op تماماً.
     */
    fun speakGreetingOnce() {
        if (!greeted.compareAndSet(false, true)) return

        executor.execute {
            // التحية لا تُنطق إلا إذا سمحت آلة الحالة بالانتقال إلى SPEAKING
            if (!machine.beginSpeaking()) return@execute
            try {
                record(chain.speak(GREETING, voice))
            } finally {
                machine.doneSpeaking()
            }
        }
    }

    /** نطق نص عربي (مطبَّع داخلياً عبر السلسلة) — يُسلسل خلفياً. */
    fun speak(text: String) {
        executor.execute {
            if (!machine.beginSpeaking()) return@execute
            try {
                record(chain.speak(text, voice))
            } finally {
                machine.doneSpeaking()
            }
        }
    }

    /** التحية الرسمية — نص ثابت معلن (قابل للاختبار مباشرة). */
    companion object {
        const val GREETING = "أهلاً بك. أنا جارفيس، في خدمتك."

        /** بناء الجلسة الكاملة الحقيقية: Sherpa أساسي ← النظام fallback أخيراً. */
        fun createDefault(
            appContext: android.content.Context,
            machine: JarvisMachineAdapter,
            onResult: (TtsResult) -> Unit = {},
        ): JarvisVoiceSession {
            val sherpa = SherpaOnnxTtsProvider(appContext)
            val system = AndroidSystemTtsProvider(appContext)
            // الترتيب بنيوي مُفرض في TtsFallbackChain: NEURAL أولاً، SYSTEM آخراً
            val chain = TtsFallbackChain(listOf(sherpa, system))
            return JarvisVoiceSession(chain, machine, onResult = onResult)
        }
    }
}
