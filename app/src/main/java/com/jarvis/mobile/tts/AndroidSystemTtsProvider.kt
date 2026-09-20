package com.jarvis.mobile.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * طبقة SYSTEM — محرك النظام (fallback أخير فقط — ADR-7/11).
 *
 * العقد:
 * - لا يُبنى أساساً في السلسلة أبداً (بنيوياً في [TtsFallbackChain]).
 * - يبذل جهده لتحقيق شخصية Jarvis: pitch/rate ضمن حدود [JarvisVoiceSpec] عبر
 *   TextToSpeech.setPitch/setSpeechRate — مع توثيق صريح أن جودة النطق العربي
 *   وشخصية "العميق الفخم الطبيعي" أضعف من طبقة NEURAL (حدود الطبقة معلنة).
 * - لا نجاح كاذب: عطل المحرك أو غياب عربي → [TtsResult.NotSupported] أو استثناء
 *   (تلتقطه السلسلة) — لا صوت صامت يُحسب نجاحاً.
 */
class AndroidSystemTtsProvider(
    private val appContext: Context,
    /** نقطة حقن المزامنة للاختبار — الافتراضي: مزامنة حقيقية على utterance. */
    private val awaitCompletion: AwaitCompletion = DefaultAwaitCompletion,
) : TtsProvider {

    override val tier: TtsTier = TtsTier.SYSTEM

    /** ينتظر انتهاء نطق utterance فعلياً — true إن اكتمل بنجاح. */
    fun interface AwaitCompletion {
        fun await(tts: TextToSpeech, utteranceId: String, timeoutMs: Long): Boolean
    }

    object DefaultAwaitCompletion : AwaitCompletion {
        override fun await(tts: TextToSpeech, utteranceId: String, timeoutMs: Long): Boolean {
            val latch = CountDownLatch(1)
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(u: String?) {
                    if (u == utteranceId) {
                        done.set(true)
                        latch.countDown()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    latch.countDown()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    latch.countDown()
                }
            })
            val finished = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            return finished && done.get()
        }
    }

    private var tts: TextToSpeech? = null
    private var initStatus: Int? = null

    /** تهيئة محرك النظام — غير حاجبة في TextToSpeech، لذا ننتظر النتيجة هنا. */
    override fun prepare() {
        if (tts != null) return
        val created = TextToSpeech(appContext) { status -> initStatus = status }
        tts = created
        // انتظار قصير لاكتمال تهيئة المحرك (callbacks غير متزامن)
        val deadline = System.currentTimeMillis() + 3_000
        while (initStatus == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        if (initStatus != TextToSpeech.SUCCESS) {
            throw IllegalStateException("فشل تهيئة محرك النظام (status=$initStatus)")
        }
        val result = created.setLanguage(Locale("ar"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // لا نُجهز محركاً لا ينطق عربي — NotSupported أنظف من صوت خاطئ
            throw IllegalStateException("محرك النظام لا يدعم العربية (result=$result)")
        }
    }

    override fun speak(text: String, voice: JarvisVoiceSpec): TtsResult {
        val engine = tts ?: throw IllegalStateException("المحرك غير مهيأ — استدعِ prepare() أولاً")

        // شخصية Jarvis ضمن حدود المواصفة: pitchFactor وrateFactor يعبران للطرفين بنفس
        // دلالتهما — كلاهما معامل "مضاعف" (1.0 = الطبيعي؛ pitchFactor أصغر = أعمق،
        // وrateFactor أصغر = أهدأ/أبطأ). لا قلب لأي منهما: القلب يعكس الشخصية (§3.1).
        engine.setPitch(voice.pitchFactor)
        engine.setSpeechRate(voice.rateFactor)

        val utteranceId = "jarvis-system-${System.nanoTime()}"
        val status = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (status != TextToSpeech.SUCCESS) {
            return TtsResult.NotSupported(tier, "رفض محرك النظام الطلب (status=$status)")
        }

        val completed = awaitCompletion.await(engine, utteranceId, TIMEOUT_MS)
        return if (completed) {
            TtsResult.Success(tier, "محرك النظام نطق ${text.length} حرفاً (جودة أقل من NEURAL — حدود الطبقة معلنة)")
        } else {
            TtsResult.NotSupported(tier, "لم يكتمل النطق خلال ${TIMEOUT_MS}ms")
        }
    }

    fun shutdown() {
        tts?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (_: Exception) {
            }
        }
        tts = null
        initStatus = null
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
