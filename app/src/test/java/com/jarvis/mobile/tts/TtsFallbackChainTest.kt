package com.jarvis.mobile.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات عقد سلسلة طبقات TTS (Phase 7 — ADR-7/11 المنقح + المواصفة الصوتية §3.1):
 * - الترتيب الإلزامي: NEURAL ← CLOUD ← SYSTEM (System TTS آخر طبقة لا أولها).
 * - Fallback تلقائي عند الفشل/عدم الدعم.
 * - كل طبقة تُطالَب بـJarvisVoiceSpec (لا صوت بلا شخصية).
 * - التطبيع يُطبَّق قبل أي طبقة.
 * - SAFE FAILURE: فشل الكل → SafeFailure بلا استثناء.
 */
class TtsFallbackChainTest {

    /** طبقة وهمية قابلة للبرمجة للاختبار. */
    private class FakeProvider(
        override val tier: TtsTier,
        private val result: TtsResult? = null,
        private val throwOn: ((String, JarvisVoiceSpec) -> Boolean)? = null,
        private val throwOnPrepare: Boolean = false,
    ) : TtsProvider {
        var prepareCount: Int = 0
            private set

        val prepareCalled: Boolean get() = prepareCount > 0

        override fun prepare() {
            prepareCount++
            if (throwOnPrepare) throw IllegalStateException("فشل تهيئة مفتعل للطبقة $tier")
        }

        override fun speak(text: String, voice: JarvisVoiceSpec): TtsResult {
            if (throwOn?.invoke(text, voice) == true) throw IllegalStateException("فشل مفتعل للطبقة $tier")
            return result ?: TtsResult.Success(tier, "${tier.name}:${text.length}:${voice.pitchFactor}")
        }
    }

    private val spec = JarvisVoiceSpec.JARVIS

    @Test
    fun `الطبقة الأساسية هي NEURAL دائما عندما تدعم وتنجح`() {
        val neural = FakeProvider(TtsTier.NEURAL)
        val system = FakeProvider(TtsTier.SYSTEM)
        val chain = TtsFallbackChain(listOf(neural, system))

        val result = chain.speak("المهمة اكتملت", spec)

        assertTrue(result is TtsResult.Success)
        assertEquals(TtsTier.NEURAL, (result as TtsResult.Success).tier)
        assertFalse("الطبقة الأدنى لا تُلمس عند نجاح الأعلى", system.prepareCalled)
    }

    @Test
    fun `فشل NEURAL يستدعي CLOUD تلقائيا`() {
        val neural = FakeProvider(TtsTier.NEURAL, throwOn = { _, _ -> true })
        val cloud = FakeProvider(TtsTier.CLOUD)
        val chain = TtsFallbackChain(listOf(neural, cloud))

        val result = chain.speak("مرحباً", spec)

        assertTrue(result is TtsResult.Success)
        assertEquals(TtsTier.CLOUD, (result as TtsResult.Success).tier)
    }

    @Test
    fun `عدم دعم NEURAL يستدعي SYSTEM - النظام آخر طبقة لا أولها`() {
        val neural = FakeProvider(TtsTier.NEURAL, result = TtsResult.NotSupported(TtsTier.NEURAL, "لا نموذج مثبت"))
        val system = FakeProvider(TtsTier.SYSTEM)
        val chain = TtsFallbackChain(listOf(neural, system))

        val result = chain.speak("مرحباً", spec)

        assertTrue(result is TtsResult.Success)
        assertEquals(TtsTier.SYSTEM, (result as TtsResult.Success).tier)
    }

    @Test
    fun `سلسلة كاملة NEURAL ثم CLOUD ثم SYSTEM عند تعطل الأولى والثانية`() {
        val neural = FakeProvider(TtsTier.NEURAL, throwOn = { _, _ -> true })
        val cloud = FakeProvider(TtsTier.CLOUD, result = TtsResult.NotSupported(TtsTier.CLOUD, "لا شبكة"))
        val system = FakeProvider(TtsTier.SYSTEM)
        val chain = TtsFallbackChain(listOf(neural, cloud, system))

        val result = chain.speak("اختبار السلسلة", spec)

        assertEquals(TtsTier.SYSTEM, (result as TtsResult.Success).tier)
    }

    @Test
    fun `السلسلة تهيّئ الطبقة تلقائيا مرة واحدة عند أول نطق فعلي`() {
        val neural = FakeProvider(TtsTier.NEURAL)
        val chain = TtsFallbackChain(listOf(neural))

        chain.speak("استعد", spec)
        chain.speak("مرة أخرى", spec)

        assertEquals("التهيئة الثقيلة لا تُعاد لكل نطق", 1, neural.prepareCount)
    }

    @Test
    fun `فشل تهيئة NEURAL يسقط لـSYSTEM بلا انهيار`() {
        val neural = FakeProvider(TtsTier.NEURAL, throwOnPrepare = true)
        val system = FakeProvider(TtsTier.SYSTEM)
        val chain = TtsFallbackChain(listOf(neural, system))

        val result = chain.speak("مرحباً", spec)

        assertEquals(TtsTier.SYSTEM, (result as TtsResult.Success).tier)
        assertEquals("فشل عابر لا يُثبَّت — يُعاد في الطلب التالي", 1, neural.prepareCount)
        chain.speak("مرة أخرى", spec)
        assertEquals(2, neural.prepareCount)
    }

    @Test
    fun `فشل كل الطبقات يعطي SafeFailure بلا استثناء - safe failure`() {
        val failing = { tier: TtsTier -> FakeProvider(tier, throwOn = { _, _ -> true }) }
        val chain = TtsFallbackChain(listOf(failing(TtsTier.NEURAL), failing(TtsTier.CLOUD), failing(TtsTier.SYSTEM)))

        val result = chain.speak("أي شيء", spec)

        assertTrue(result is TtsResult.SafeFailure)
        assertEquals(3, (result as TtsResult.SafeFailure).attempted.size)
    }

    @Test
    fun `سلسلة بلا طبقات تعطي SafeFailure فورا - لا انهيار`() {
        val chain = TtsFallbackChain(emptyList())
        val result = chain.speak("نص", spec)
        assertTrue(result is TtsResult.SafeFailure)
    }

    @Test
    fun `كل طبقة تستلم شخصية Jarvis نفسها - لا صوت بلا مواصفة`() {
        var receivedVoice: JarvisVoiceSpec? = null
        val spy = object : TtsProvider {
            override val tier = TtsTier.NEURAL
            override fun prepare() {}
            override fun speak(text: String, voice: JarvisVoiceSpec): TtsResult {
                receivedVoice = voice
                return TtsResult.Success(tier, "ok")
            }
        }
        TtsFallbackChain(listOf(spy)).speak("نص", spec)

        assertEquals(spec, receivedVoice)
        assertEquals(0.85f, receivedVoice?.pitchFactor)
    }

    @Test
    fun `النص يُطبَّع قبل وصوله لأي طبقة`() {
        var receivedText: String? = null
        val spy = object : TtsProvider {
            override val tier = TtsTier.NEURAL
            override fun prepare() {}
            override fun speak(text: String, voice: JarvisVoiceSpec): TtsResult {
                receivedText = text
                return TtsResult.Success(tier, "ok")
            }
        }
        TtsFallbackChain(listOf(spy)).speak("لديك 3 مهام", JarvisVoiceSpec.JARVIS)

        assertEquals("النص المُطبَّع هو ما يُنطق", "لديك ثلاثة مهام", receivedText)
    }

    @Test
    fun `تطبيع فارغ لا يمنع النطق - الطبقة تستلم ما تبقى`() {
        var received: String? = null
        val spy = object : TtsProvider {
            override val tier = TtsTier.NEURAL
            override fun prepare() {}
            override fun speak(text: String, voice: JarvisVoiceSpec): TtsResult {
                received = text
                return TtsResult.Success(tier, "ok")
            }
        }
        TtsFallbackChain(listOf(spy)).speak("   ", JarvisVoiceSpec.JARVIS)
        // فارغ → يبقى فارغاً وتُترك قرار النطق للطبقة (بلا انهيار في السلسلة)
        assertEquals("   ", received)
    }

    @Test
    fun `السلسلة ترفض بنائها بترتيب معكوس أو مكرر - عقدها صارم`() {
        // ترتيب معكوس
        try {
            TtsFallbackChain(listOf(FakeProvider(TtsTier.SYSTEM), FakeProvider(TtsTier.NEURAL)))
            org.junit.Assert.fail("الترتيب المعكوس مرفوض — System ليس أساساً")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
        // تكرار
        try {
            TtsFallbackChain(listOf(FakeProvider(TtsTier.NEURAL), FakeProvider(TtsTier.NEURAL)))
            org.junit.Assert.fail("تكرار نفس الطبقة مرفوض")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
        // تخطي طبقة NEURAL (تبدأ بـSYSTEM) — System لا يجوز أن يكون أساساً
        try {
            TtsFallbackChain(listOf(FakeProvider(TtsTier.SYSTEM)))
            org.junit.Assert.fail("سلسلة تبدأ بـSYSTEM مرفوضة — System fallback فقط")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }
}
