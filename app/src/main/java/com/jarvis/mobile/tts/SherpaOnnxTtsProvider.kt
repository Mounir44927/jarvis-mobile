package com.jarvis.mobile.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/**
 * طبقة NEURAL الأساسية — sherpa-onnx v1.13.8 (Apache-2.0) بنموذج Piper العربي الرجولي
 * `vits-piper-ar_JO-kareem-medium-int8` (المواصفة §3.1: عربي رجولي عميق هادئ طبيعي).
 *
 * العقد الصارم: هذا المزود **لا يُنتج صوتاً كاذباً أبداً**:
 * - فشل تحميل النموذج أو المحرك → [TtsResult.NotSupported] (تنتقل السلسلة للـfallback).
 * - إنتاج 0 عينة → [TtsResult.NotSupported] (لا صوت صامت يُعدّ نجاحاً).
 * - فشل التشغيل الصوتي → استثناء → السلسلة تلتقطه وتهبط للطبقة التالية.
 *
 * النموذج مضمّن في assets (v1.13.8 يقرأ الأصول عبر AssetManager مباشرة):
 * tts/vits-piper-ar_JO-kareem-medium-int8/{ar_JO-kareem-medium.onnx, tokens.txt, espeak-ng-data/}
 *
 * مساران مختلفان لنوعين مختلفين من الملفات (تحقق بالقراءة من مصدر v1.13.8):
 * 1. الموديل + tokens: يقرأهما sherpa-onnx أصلياً من assets بنمط AASSET_MODE_BUFFER →
 *    تبقى مسارات نسبية، ويجب أن تكون غير مضغوطة في APK (noCompress: onnx/txt).
 * 2. espeak-ng-data: يقرأه espeak-ng بـfopen من نظام ملفات حقيقي → لا يعمل من assets،
 *    ويُنسخ إلى filesDir عبر [EspeakDataInstaller] ويُمرَّر مساراً مطلقاً في dataDir.
 *    (تجاهل هذا = "SHERPA_ONNX_EXIT(-1)" داخل InitEspeak عند أول نطق.)
 *
 * LspConfig عبر java-api غير متاح في AAR v1.13.8 (تم فحص classes.jar) — لا حاجة له هنا:
 * صوت espeak (ar_JO) يأتي من ميتاداتا الموديل نفسها (voice/language في ONNX).
 *
 * سلامة قبل الدخول للأصلي: تُتحقق الأصول المطلوبة أولاً — فشل التحقق = استثناء من
 * [prepare] تعزله [TtsFallbackChain] إلى NotSupported ثم SYSTEM، بلا انهيار عملية.
 */
class SherpaOnnxTtsProvider(
    private val appContext: Context,
    /** نقطة الحقن للاختبار — الافتراضي المحرك الحقيقي v1.13.8. */
    private val engineFactory: SherpaEngineFactory = DefaultSherpaEngineFactory,
) : TtsProvider {

    override val tier: TtsTier = TtsTier.NEURAL

    /** واجهة المحرك التي يعتمد عليها المزود — تجعل الاختبار ممكناً بلا مكتبة JNI. */
    interface SherpaEngine {
        val sampleRate: Int
        fun generate(text: String, speed: Float): FloatArray
        fun release()
    }

    /** يبني محرك sherpa-onnx حقيقي (تحميل JNI + النموذج). */
    fun interface SherpaEngineFactory {
        fun create(appContext: Context): SherpaEngine
    }

    /** المحرك الحقيقي — الاعتماد الوحيد على كلاسات sherpa-onnx في المشروع كله. */
    object DefaultSherpaEngineFactory : SherpaEngineFactory {
        override fun create(appContext: Context): SherpaEngine {
            requireAssets(appContext)
            val espeakDataDir = EspeakDataInstaller.forAppAssets(
                appContext = appContext,
                sourceRoot = ESPEAK_DIR_ASSET,
            ).installIfNeeded()

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = MODEL_ASSET,
                        tokens = TOKENS_ASSET,
                        // مسار مطلق إلزامي: espeak-ng لا يقرأ من assets (انظر [EspeakDataInstaller])
                        dataDir = espeakDataDir.absolutePath,
                        // لا lengthScale هنا: الإيقاع يأتي كاملاً من voice.rateFactor عبر speed
                        // في generate (شيربا يشتق length_scale = 1/speed ويتجاهل قيمة الإعداد
                        // كلما مُرّرت speed ≠ 1) — مصدر واحد للإيقاع لا مصدران متضاربان.
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
            )
            val tts = OfflineTts(assetManager = appContext.assets, config = config)
            return SherpaEngineImpl(tts)
        }

        /**
         * تحقق مسبق من وجود الأصول المطلوبة قبل استدعاء الطبقة الأصلية.
         * سبب وجوده: قراءة أصل مفقود داخل sherpa-onnx تنتهي بـSHERPA_ONNX_EXIT(-1)
         * (إنهاء العملية) لا باستثناء — فالتحقق هنا يحوّل الخطأ الشائع إلى fallback نظيف.
         */
        private fun requireAssets(appContext: Context) {
            // 1) ملفان يُقرآن أصلياً من assets — غيابهما = قراءة فاشلة داخل JNI
            for (path in listOf(MODEL_ASSET, TOKENS_ASSET)) {
                val exists = try {
                    appContext.assets.open(path).use { }
                    true
                } catch (_: java.io.IOException) {
                    false
                }
                check(exists) { "أصل مفقود في assets: $path" }
            }
            // 2) شجرة espeak-ng-data — تُفحص كمجلد (فتح مجلد كملف لا يفشل دائماً)
            check(appContext.assets.list(ESPEAK_DIR_ASSET)?.isNotEmpty() == true) {
                "مجلد بيانات espeak مفقود في assets: $ESPEAK_DIR_ASSET"
            }
        }

        /** مسار الموديل داخل assets — Piper ar_JO-kareem medium (int8). */
        const val MODEL_ASSET = "$MODEL_DIR/ar_JO-kareem-medium.onnx"
        const val TOKENS_ASSET = "$MODEL_DIR/tokens.txt"
        const val ESPEAK_DIR_ASSET = "$MODEL_DIR/${EspeakDataInstaller.ESPEAK_ASSET_DIR}"

        private const val MODEL_DIR = "tts/vits-piper-ar_JO-kareem-medium-int8"
    }

    private class SherpaEngineImpl(private val tts: OfflineTts) : SherpaEngine {
        override val sampleRate: Int = tts.sampleRate()

        override fun generate(text: String, speed: Float): FloatArray {
            // sid=0: النموذج أحادي المتحدث (MODEL_CARD: Speakers: 1)
            return tts.generate(text = text, sid = 0, speed = speed).samples
        }

        override fun release() = tts.release()
    }

    private var engine: SherpaEngine? = null
    private var track: AudioTrack? = null

    @Volatile
    private var prepared = false

    /**
     * تهيئة ثقيلة (مرة واحدة): نسخ espeak-ng-data عند الحاجة + تحميل libsherpa-onnx-jni
     * ونموذج ONNX من assets. تُستدعى من [TtsFallbackChain] قبل أول نطق على منفذ خلفي.
     *
     * قد ترمي استثناءً (نموذج ناقص/فشل JNI) — السلسلة تعزله إلى NotSupported ثم SYSTEM.
     */
    override fun prepare() {
        if (prepared) return
        engine = engineFactory.create(appContext)
        prepared = true
    }

    override fun speak(text: String, voice: JarvisVoiceSpec): TtsResult {
        val eng = engine ?: run {
            // السلسلة تستدعي prepare() قبل speak() — غياب المحرك يعني فشل تهيئة سابق
            return TtsResult.NotSupported(tier, "المحرك غير مهيأ — فشل تحميل sherpa-onnx/النموذج")
        }

        val samples = try {
            // sherpa-onnx: speed معامل *سرعة* (أكبر = أسرع؛ length_scale = 1/speed داخلياً).
            // rateFactor في [0.8, 1.0] = إيقاع هادئ غير متعجل (أقل من الطبيعي) يُمرَّر كما هو —
            // لا يُقلَب (قلبه كان يجعل الشخصية أسرع من الطبيعي، مخالفاً §3.1)
            eng.generate(text, speed = voice.rateFactor)
        } catch (e: Exception) {
            return TtsResult.NotSupported(tier, "فشل توليد الصوت: ${e::class.java.simpleName}")
        }

        if (samples.isEmpty()) {
            // لا صوت كاذب: توليد فارغ ليس نجاحاً
            return TtsResult.NotSupported(tier, "توليد فارغ (0 عينة)")
        }

        return try {
            playBlocking(samples, eng.sampleRate)
            TtsResult.Success(tier, "sherpa-onnx kareem ar_JO ${samples.size} samples @${eng.sampleRate}Hz")
        } catch (e: Exception) {
            throw IllegalStateException("فشل تشغيل الصوت (AudioTrack): ${e::class.java.simpleName}", e)
        }
    }

    /** تشغيل حجب للعينة — الطبقة الحالية هي الموقع الوحيد للتشغيل. */
    private fun playBlocking(samples: FloatArray, sampleRate: Int) {
        val channelMask = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            encoding,
        ).coerceAtLeast(sampleRate / 2) // لا buffer ضيق يسبب underrun

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(encoding)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf * Float.SIZE_BYTES)
            .build()

        track = t
        try {
            t.play()
            t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            // تصريف الـbuffer حتى نهاية الصوت الفعلي — لا قطع للنهاية
            while (t.playbackHeadPosition < samples.size) {
                Thread.sleep(20)
            }
        } finally {
            try {
                t.stop()
            } catch (_: IllegalStateException) {
            }
            t.release()
            if (track === t) track = null
        }
    }

    /** تحرير الموارد الثقيلة (نموذج ONNX) — يُستدعى من الجلسة عند إغلاق التطبيق. */
    fun shutdown() {
        track?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        track = null
        engine?.release()
        engine = null
        prepared = false
    }
}
