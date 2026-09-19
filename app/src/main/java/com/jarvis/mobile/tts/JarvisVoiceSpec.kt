package com.jarvis.mobile.tts

/**
 * جنس الصوت — المواصفة الصوتية لـJarvis: رجولي.
 */
enum class VoiceGender { MALE, FEMALE }

/**
 * وسوم أسلوب شخصية Jarvis — من المواصفة الصوتية الرسمية (ARCHITECTURE §3.1):
 * عربي رجولي، عميق ومنخفض نسبياً، خشن قليلاً طبيعياً بلا مبالغة، فخم قوي هادئ واثق، طبيعي غير روبوتي.
 */
enum class VoiceStyleTag(val label: String) {
    DEEP("عميق"),
    SLIGHTLY_ROUGH("خشن قليلاً طبيعياً"),
    CALM("هادئ"),
    CONFIDENT("واثق"),
    LUXURIOUS("فخم"),
}

/**
 * واصف شخصية الصوت (Phase 7 — ADR-7/11 المنقح).
 *
 * هذا هو العقد الوحيد الذي تلتزم به كل طبقات TTS (TtsTier): كل طبقة تبذل جهدها
 * لتحقيق هذه المواصفة وتُوثّق حدودها بصراحة — لا طبقة تتجاهله ولا تخرج عن حدوده.
 *
 * حدود المعاملات مُفرضة بـrequire (لا حالة غير صالحة تُبنى أصلاً):
 * - pitchFactor في [0.7, 1.0]: النطاق "العميق/المنخفض نسبياً" — أقل من 0.7 تشويه غير طبيعي،
 *   وأعلى من 1.0 يخالف الرجولية العميقة المطلوبة.
 * - rateFactor في [0.8, 1.0]: الإيقاع "الهادئ الواثق غير المتعجل" — أسرع من الطبيعي يخالف الشخصية.
 * - raspIntensity في [0, 0.4]: خشونة "قليلاً وبلا مبالغة" — فوق 0.4 صوت مفتعل/مهتز.
 */
data class JarvisVoiceSpec(
    val gender: VoiceGender = VoiceGender.MALE,
    val languageTag: String = "ar",
    val pitchFactor: Float = 0.85f,
    val rateFactor: Float = 0.92f,
    val raspIntensity: Float = 0.2f,
    val styleTags: Set<VoiceStyleTag> = setOf(
        VoiceStyleTag.DEEP,
        VoiceStyleTag.SLIGHTLY_ROUGH,
        VoiceStyleTag.CALM,
        VoiceStyleTag.CONFIDENT,
        VoiceStyleTag.LUXURIOUS,
    ),
) {
    init {
        require(languageTag.startsWith("ar")) { "شخصية Jarvis عربية أولاً — languageTag غير عربي مرفوض" }
        require(pitchFactor in PITCH_MIN..PITCH_MAX) {
            "pitchFactor خارج نطاق الشخصية العميقة [$PITCH_MIN, $PITCH_MAX]: $pitchFactor"
        }
        require(rateFactor in RATE_MIN..RATE_MAX) {
            "rateFactor خارج نطاق الإيقاع الهادئ [$RATE_MIN, $RATE_MAX]: $rateFactor"
        }
        require(raspIntensity in RASP_MIN..RASP_MAX) {
            "raspIntensity خارج نطاق الخشونة الطبيعية البسيطة [$RASP_MIN, $RASP_MAX]: $raspIntensity"
        }
        require(VoiceStyleTag.DEEP in styleTags && VoiceStyleTag.CALM in styleTags) {
            "شخصية Jarvis تتطلب على الأقل وسمَي DEEP وCALM (المواصفة §3.1)"
        }
    }

    companion object {
        const val PITCH_MIN = 0.7f
        const val PITCH_MAX = 1.0f
        const val RATE_MIN = 0.8f
        const val RATE_MAX = 1.0f
        const val RASP_MIN = 0f
        const val RASP_MAX = 0.4f

        /** الشخصية الرسمية الوحيدة لـJarvis — لا شخصيات بديلة في هذه المرحلة. */
        val JARVIS = JarvisVoiceSpec()
    }
}
