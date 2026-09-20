package com.jarvis.mobile.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * اختبارات ربط الصوت بواجهة المستخدم (Phase 7 — ربط TtsFallbackChain):
 *
 * 1. منع تكرار التحية بسبب recomposition/lifecycle — حاجز مرة واحدة بنيوي.
 * 2. منع تجاوز ArabicTextNormalizer — كل نص يمر عبر TtsFallbackChain (تطبّع داخلي).
 * 3. منع تجاوز TtsFallbackChain — UI لا يستدعي أي مزود مباشرة.
 * 4. عدم تجاهل fallback — فشل NEURAL الحقيقي → SYSTEM يُستخدم فعلاً.
 * 5. لا نجاح كاذب — مزود بلا محرك/بتوليد فارغ ليس Success أبداً.
 */
class JarvisVoiceSessionTest {

    /** مزود مسجَّل يستقبل النصوص والشخصية — يثبت ما الذي مرّ عبره فعلاً. */
    private class RecordingProvider(
        override val tier: TtsTier,
        private val fail: Boolean = false,
    ) : TtsProvider {
        val receivedTexts = mutableListOf<String>()
        val receivedVoices = mutableListOf<JarvisVoiceSpec>()
        var prepareCalls = 0
            private set

        override fun prepare() {
            prepareCalls++
        }

        override fun speak(text: String, voice: JarvisVoiceSpec): TtsResult {
            receivedTexts.add(text)
            receivedVoices.add(voice)
            return if (fail) {
                TtsResult.NotSupported(tier, "فشل مبرمج للاختبار")
            } else {
                TtsResult.Success(tier, "ok")
            }
        }
    }

    /** آلة حالة وهمية تسجل أوامر SPEAKING. */
    private class FakeMachine : JarvisVoiceSession.JarvisMachineAdapter {
        var begins = 0
        var dones = 0
        var allowSpeaking = true

        override fun beginSpeaking(): Boolean {
            begins++
            return allowSpeaking
        }

        override fun doneSpeaking() {
            dones++
        }
    }

    private class Harness(val neural: RecordingProvider, val system: RecordingProvider) {
        val machine = FakeMachine()
        val chain = TtsFallbackChain(listOf(neural, system))
        val results = mutableListOf<TtsResult>()
        val latch = CountDownLatch(1)

        /** منفذ الجلسة نفسه تحت سيطرة الاختبار — يسمح بحاجز ترتيبي حتمي. */
        private val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "jarvis-voice-test").apply { isDaemon = true }
        }

        val session = JarvisVoiceSession(
            chain = chain,
            machine = machine,
            executor = executor,
            onResult = {
                results.add(it)
                latch.countDown()
            },
        )

        /**
         * حاجز ترتيبي حتمي: عند تنفيذ هذه المهمة تكون كل مهام المنفذ السابقة قد
         * انتهت فعلاً. يُستخدم حيث لا نتيجة متوقعة (رفض آلة الحالة لا يُسجَّل كنتيجة).
         */
        fun awaitIdle() {
            val fence = CountDownLatch(1)
            executor.execute { fence.countDown() }
            assertTrue("انتهت مهام المنفذ خلال المهلة", fence.await(5, TimeUnit.SECONDS))
        }
    }

    // ---------------------------------------------------------------
    // 1) منع تكرار التحية — recomposition/lifecycle لا يعيدان النطق
    // ---------------------------------------------------------------

    @Test
    fun `التحية تُنطق مرة واحدة فقط مهما تكرر الاستدعاء - حاجز recomposition`() {
        val harness = Harness(RecordingProvider(TtsTier.NEURAL), RecordingProvider(TtsTier.SYSTEM))

        // محاكاة recomposition متعدد + إعادة إنشاء Activity: استدعاءات متكررة
        repeat(5) { harness.session.speakGreetingOnce() }
        assertTrue(harness.latch.await(5, TimeUnit.SECONDS))

        assertEquals("نُطقت التحية مرة واحدة بالضبط", 1, harness.neural.receivedTexts.size)
        assertEquals("fallback لم يُلمس", 0, harness.system.receivedTexts.size)
        assertEquals(1, harness.machine.begins)
        assertEquals(1, harness.machine.dones)
    }

    @Test
    fun `التحية المرفوضة من آلة الحالة لا تُنطق ولا تسجل نجاحا كاذبا`() {
        val harness = Harness(RecordingProvider(TtsTier.NEURAL), RecordingProvider(TtsTier.SYSTEM))
        harness.machine.allowSpeaking = false

        harness.session.speakGreetingOnce()
        // الرفض إشارة نجاح للامتناع لا نتيجة — لذا ننتظر فراغ المنفذ لا وصول نتيجة
        harness.awaitIdle()

        assertEquals("آلة الحالة رفضت SPEAKING → لا نطق", 0, harness.neural.receivedTexts.size)
        assertEquals("لا fallback ولا لمس للطبقة الأدنى", 0, harness.system.receivedTexts.size)
        assertTrue("الرفض لا يُسجَّل لا نجاحاً ولا فشلاً", harness.results.isEmpty())
        assertEquals("المحاولة رُصدت مرة واحدة", 1, harness.machine.begins)
        assertEquals("لا إعادة للآلة بعد محاولة لم تبدأ", 0, harness.machine.dones)
    }

    @Test
    fun `النص المرسل عبر speak يمر بالسلسلة ويحمل شخصية Jarvis الرسمية`() {
        val harness = Harness(RecordingProvider(TtsTier.NEURAL), RecordingProvider(TtsTier.SYSTEM))

        harness.session.speak("لديك 3 مهام")
        assertTrue(harness.latch.await(5, TimeUnit.SECONDS))

        val voice = harness.neural.receivedVoices.single()
        assertEquals(JarvisVoiceSpec.JARVIS, voice)
        // التطبيع عبر السلسلة: "3" → "ثلاثة"
        assertEquals("لديك ثلاثة مهام", harness.neural.receivedTexts.single())
    }

    // ---------------------------------------------------------------
    // 2) منع تجاوز ArabicTextNormalizer — التطبيع يحدث داخل السلسلة دائماً
    // ---------------------------------------------------------------

    @Test
    fun `لا نص يصل لأي مزود دون تطبيع - أرقام ولاتيني ورموز`() {
        val harness = Harness(RecordingProvider(TtsTier.NEURAL), RecordingProvider(TtsTier.SYSTEM))

        harness.session.speak("اتصل بjarvis على 0100 20 30 40")
        assertTrue(harness.latch.await(5, TimeUnit.SECONDS))

        val spoken = harness.neural.receivedTexts.single()
        // jarvis → جارفيس + الأرقام مقروءة عربياً (أثر التطبيع الإلزامي)
        assertTrue("لا لاتيني يصل للمزود: \"$spoken\"", spoken.contains("جارفيس"))
        assertFalse(spoken.contains("jarvis"))
        assertFalse(spoken.contains("0100"))
    }

    @Test
    fun `التحية الرسمية نفسها تُطبَّع قبل النطق - والنتيجة مستقرة`() {
        val harness = Harness(RecordingProvider(TtsTier.NEURAL), RecordingProvider(TtsTier.SYSTEM))

        harness.session.speakGreetingOnce()
        assertTrue(harness.latch.await(5, TimeUnit.SECONDS))

        val spoken = harness.neural.receivedTexts.single()
        val expected = ArabicTextNormalizer().normalize(JarvisVoiceSession.GREETING)
        assertEquals(
            "التطبيع لا يُفسد التحية الثابتة (حتمي)",
            expected,
            spoken,
        )
        // المنطوق = التحية الرسمية كاملة (التشكيل وحده يُزال: أهلاً → أهلا) — لا كلمة تُفقد
        for (fragment in listOf("أهلا", "بك", "جارفيس", "في خدمتك")) {
            assertTrue("جزء مفقود من التحية: $fragment — المنطوق: \"$spoken\"", spoken.contains(fragment))
        }
    }

    // ---------------------------------------------------------------
    // 3) منع تجاوز TtsFallbackChain — لا استدعاء مباشر للمزودين من UI
    // ---------------------------------------------------------------

    @Test
    fun `الجلسة تستدعي السلسلة لا المزودين - prepare يُستدعى مرة لكل طبقة`() {
        val neural = RecordingProvider(TtsTier.NEURAL)
        val system = RecordingProvider(TtsTier.SYSTEM)
        val harness = Harness(neural, system)

        harness.session.speak("نص أول")
        harness.session.speak("نص ثانٍ")
        // انتظار تنفيذ الطلبين بالتسلسل (منفذ أحادي)
        assertTrue(harness.latch.await(5, TimeUnit.SECONDS))

        // كل نص وصل عبر chain.speak (تطبيع + شخصية + ترتيب + تهيئة) — لا استدعاء مباشر
        assertEquals(2, neural.receivedTexts.size)
        assertEquals(0, system.receivedTexts.size)
        assertEquals("الطبقة المستخدمة تُهيَّأ مرة واحدة فقط للطلبين", 1, neural.prepareCalls)
        assertEquals("طبقة لم تُستخدم لا تُهيَّأ إطلاقاً", 0, system.prepareCalls)
    }

    // ---------------------------------------------------------------
    // 4) fallback لا يُتجاهل — فشل NEURAL → SYSTEM فعلياً
    // ---------------------------------------------------------------

    @Test
    fun `فشل مزود NEURAL يحول فعليا إلى SYSTEM - لا تجاهل للفولباك`() {
        val neural = RecordingProvider(TtsTier.NEURAL, fail = true)
        val system = RecordingProvider(TtsTier.SYSTEM)
        val chain = TtsFallbackChain(listOf(neural, system))
        val machine = FakeMachine()
        val results = mutableListOf<TtsResult>()
        val latch = CountDownLatch(1)
        val session = JarvisVoiceSession(chain, machine, onResult = {
            results.add(it)
            latch.countDown()
        })

        session.speakGreetingOnce()
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertEquals("NEURAL فشل → SYSTEM نطق", 1, system.receivedTexts.size)
        val result = results.single() as TtsResult.Success
        assertEquals(TtsTier.SYSTEM, result.tier)
    }

    @Test
    fun `فشل كل الطبقات يعطي SafeFailure بلا نجاح كاذب`() {
        val neural = RecordingProvider(TtsTier.NEURAL, fail = true)
        val system = RecordingProvider(TtsTier.SYSTEM, fail = true)
        val chain = TtsFallbackChain(listOf(neural, system))
        val machine = FakeMachine()
        val results = mutableListOf<TtsResult>()
        val latch = CountDownLatch(1)
        val session = JarvisVoiceSession(chain, machine, onResult = {
            results.add(it)
            latch.countDown()
        })

        session.speak("نص")
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertTrue(results.single() is TtsResult.SafeFailure)
    }

    // ---------------------------------------------------------------
    // 5) المزودون الحقيقيون: لا نجاح كاذب قبل التهيئة
    // ---------------------------------------------------------------

    @Test
    fun `SherpaOnnxTtsProvider بلا تهيئة لا يزيف نجاحا`() {
        // appContext غير مستعمل قبل prepare (فقط factory الحقيقي يحتاجه)
        val provider = SherpaOnnxTtsProvider(
            appContext = android.app.Application(),
            engineFactory = { throw IllegalStateException("لا يجب استدعاؤه قبل prepare") },
        )
        val result = provider.speak("نص", JarvisVoiceSpec.JARVIS)
        assertTrue(result is TtsResult.NotSupported)
    }

    @Test
    fun `SherpaOnnxTtsProvider بتوليد فارغ لا يزيف نجاحا - لا صوت صامت`() {
        val provider = SherpaOnnxTtsProvider(
            appContext = android.app.Application(),
            engineFactory = {
                object : SherpaOnnxTtsProvider.SherpaEngine {
                    override val sampleRate = 22050
                    override fun generate(text: String, speed: Float) = FloatArray(0)
                    override fun release() {}
                }
            },
        )
        provider.prepare()
        val result = provider.speak("نص", JarvisVoiceSpec.JARVIS)
        assertTrue("توليد 0 عينة ليس نجاحاً", result is TtsResult.NotSupported)
    }

    @Test
    fun `SherpaOnnxTtsProvider بالتوليد الحقيقي يعيد Success ويطبق سرعة الشخصية`() {
        var requestedSpeed: Float? = null
        val provider = SherpaOnnxTtsProvider(
            appContext = android.app.Application(),
            engineFactory = {
                object : SherpaOnnxTtsProvider.SherpaEngine {
                    override val sampleRate = 22050
                    override fun generate(text: String, speed: Float): FloatArray {
                        requestedSpeed = speed
                        return FloatArray(4410) // 0.2s — لا يُشغَّل فعلياً في JVM اختبار AudioTrack
                    }

                    override fun release() {}
                }
            },
        )
        provider.prepare()

        // على JVM اختبار الوحدة: AudioTrack سيفشل (returnDefaultValues يعيد قيماً
        // افتراضية لكن البناء قد يرمي) — المطلوب فقط إثبات مسار التوليد + Success
        // قبل التشغيل، لذا نفحص سرعة الشخصية المطبقة: rateFactor كما هي (0.92 = أهدأ
        // من الطبيعي). القلب (1/rateFactor = 1.087) يعني أسرع من الطبيعي — مخالف لـ§3.1.
        val result = try {
            provider.speak("نص", JarvisVoiceSpec.JARVIS)
        } catch (e: IllegalStateException) {
            // فشل AudioTrack على JVM متوقع — الاستثناء إشارة فشل مشروعة تُنزل السلسلة
            assertEquals(JarvisVoiceSpec.JARVIS.rateFactor, requestedSpeed!!, 0.0001f)
            return
        }
        // إن لم يرمِ (بيئة تدعم AudioTrack): يجب أن يكون نجاحاً حقيقياً
        assertTrue(result is TtsResult.Success)
        assertEquals(JarvisVoiceSpec.JARVIS.rateFactor, requestedSpeed!!, 0.0001f)
    }

    // ---------------------------------------------------------------
    // 6) سرعة التسلسل — الطلبات المتزامنة تُنفذ واحدة تلو الأخرى
    // ---------------------------------------------------------------

    @Test
    fun `الطلبات المتزامنة تُسلسل ولا تتصادم - منفذ أحادي`() {
        val harness = Harness(RecordingProvider(TtsTier.NEURAL), RecordingProvider(TtsTier.SYSTEM))
        val both = CountDownLatch(2)
        val custom = JarvisVoiceSession(
            chain = harness.chain,
            machine = harness.machine,
            onResult = { both.countDown() },
        )

        custom.speak("أولاً")
        custom.speak("ثانياً")
        assertTrue(both.await(5, TimeUnit.SECONDS))

        assertEquals(2, harness.neural.receivedTexts.size)
        // الترتيب محفوظ، والنص الواصل هو الصيغة المطبَّعة (التطبيع إلزامي قبل أي طبقة)
        val normalizer = ArabicTextNormalizer()
        assertEquals(
            listOf(normalizer.normalize("أولاً"), normalizer.normalize("ثانياً")),
            harness.neural.receivedTexts,
        )
    }
}
