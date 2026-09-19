package com.jarvis.mobile.tts

/**
 * طبقات الصوت — من المواصفة الرسمية (ARCHITECTURE §3.1):
 * NEURAL أساسي ← CLOUD عند توفره ← SYSTEM fallback فقط (استخدامه كخيار رئيسي مخالف للمواصفة).
 */
enum class TtsTier { NEURAL, CLOUD, SYSTEM }

/**
 * نتيجة نطق واحدة.
 */
sealed class TtsResult {
    /** نجحت الطبقة في النطق (أو إنتاج الصوت). */
    data class Success(val tier: TtsTier, val detail: String) : TtsResult()

    /** الطبقة لا تدعم هذا الطلب (لا نموذج/لا شبكة/لغة غير مدعومة) — تنتقل السلسلة للتالي. */
    data class NotSupported(val tier: TtsTier, val reason: String) : TtsResult()

    /** فشلت كل الطبقات — SAFE FAILURE صريح بلا استثناء ولا صوت كاذب. */
    data class SafeFailure(val attempted: List<TtsTier>, val reasons: List<String>) : TtsResult()
}

/**
 * مزود صوت واحد (طبقة). التنفيذ الفعلي (sherpa-onnx / محرك سحابي / System TTS)
 * يُربط في دفعة الربط — هنا العقد فقط، قابلة للاختبار بلا Android SDK.
 */
interface TtsProvider {
    val tier: TtsTier

    /** تهيئة ثقيلة (تحميل نموذج...) — تُستدعى مرة عند بدء الجلسة. */
    fun prepare()

    /**
     * نطق نص مُطبَّع مسبقاً بشخصية محددة.
     * العقد: إما [TtsResult.Success]، أو [TtsResult.NotSupported]، أو استثناء عند عطل حقيقي
     * (السلسلة تلتقطه وتنزل للطبقة التالية — الاستثناء إشارة فشل مشروعة هنا).
     */
    fun speak(text: String, voice: JarvisVoiceSpec): TtsResult
}

/**
 * سلسلة طبقات TTS مع fallback تلقائي (Phase 7 — ADR-7/11).
 *
 * العقد:
 * 1. الترتيب الإلزامي NEURAL → CLOUD → SYSTEM بلا تكرار، والسلسلة تبدأ بـNEURAL دائماً
 *    (أو تكون فارغة → SafeFailure) — البناء يرفض غير ذلك بنيوياً: System TTS لا يجوز أن يكون
 *    أساساً، وحتى CLOUD بلا NEURAL مخالف للمواصفة (خطر "صوت Android الافتراضي" مغلق).
 * 2. كل استدعاء يُمرَّر عبر ArabicTextNormalizer قبل أي طبقة — ما يُنطق مُطبَّع دائماً.
 * 3. شخصية Jarvis تُمرَّر لكل طبقة كما هي — لا صوت بلا مواصفة.
 * 4. الفشل/عدم الدعم يهبط تلقائياً للطبقة التالية؛ فشل الكل → [TtsResult.SafeFailure] بلا استثناء.
 */
class TtsFallbackChain(
    providers: List<TtsProvider>,
    private val normalizer: ArabicTextNormalizer = ArabicTextNormalizer(),
) {
    private val ordered: List<TtsProvider>

    init {
        val tiers = providers.map { it.tier }
        require(tiers.size == tiers.distinct().size) { "طبقة مكررة في سلسلة TTS: $tiers" }
        require(tiers == tiers.sorted()) { "ترتيب الطبقات إلزامي NEURAL→CLOUD→SYSTEM، وصل: $tiers" }
        require(tiers.isEmpty() || tiers.first() == TtsTier.NEURAL) {
            "السلسلة تبدأ بـNEURAL دائماً — لا CLOUD ولا SYSTEM أساساً (المواصفة §3.1): $tiers"
        }
        ordered = providers.sortedBy { it.tier.ordinal }
    }

    /** نطق نص مع تطبيعه وتطبيق شخصية Jarvis عبر أول طبقة ناجحة. */
    fun speak(text: String, voice: JarvisVoiceSpec): TtsResult {
        val prepared = normalizer.normalize(text)
        val attempted = mutableListOf<TtsTier>()
        val reasons = mutableListOf<String>()

        for (provider in ordered) {
            attempted.add(provider.tier)
            try {
                when (val result = provider.speak(prepared, voice)) {
                    is TtsResult.Success -> return result
                    is TtsResult.NotSupported -> reasons.add("${provider.tier}: ${result.reason}")
                    is TtsResult.SafeFailure -> reasons.add("${provider.tier}: SafeFailure داخلي")
                }
            } catch (e: Exception) {
                reasons.add("${provider.tier}: ${e::class.simpleName}")
            }
        }
        return TtsResult.SafeFailure(attempted.toList(), reasons)
    }
}
