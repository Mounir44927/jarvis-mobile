package com.jarvis.mobile.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات عقد JarvisVoiceSpec (المواصفة الصوتية §3.1):
 * - الشخصية الرسمية رجولية عربية ضمن نطاق "عميق/هادئ/فخم".
 * - الحدود مُفرضة: أي قيمة خارجها تُرفض عند البناء (لا حالة غير صالحة تُبنى أصلاً).
 * - عمق التمييز: قيمة حدية صحيحة واحدة تكفي لكشف إبطال أي قيد.
 */
class JarvisVoiceSpecTest {

    @Test
    fun `الشخصية الرسمية لـ Jarvis رجولية عربية عميقة هادئة`() {
        val spec = JarvisVoiceSpec.JARVIS
        assertEquals(VoiceGender.MALE, spec.gender)
        assertTrue(spec.languageTag.startsWith("ar"))
        assertTrue("عميق: أقل من الواحد", spec.pitchFactor < 1.0f)
        assertTrue("هادئ غير متعجل", spec.rateFactor < 1.0f)
        assertTrue("خشن قليلاً بلا مبالغة", spec.raspIntensity in 0f..0.4f)
        assertTrue(VoiceStyleTag.DEEP in spec.styleTags)
        assertTrue(VoiceStyleTag.CALM in spec.styleTags)
    }

    @Test
    fun `الحدود مُفرضة - قيم خارج نطاق العمق تُرفض`() {
        try {
            JarvisVoiceSpec(pitchFactor = 0.69f)
            org.junit.Assert.fail("0.69 تحت الحد — يجب أن يُرفض")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
        try {
            JarvisVoiceSpec(pitchFactor = 1.01f)
            org.junit.Assert.fail("فوق 1.0 يخالف الرجولية العميقة")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
    }

    @Test
    fun `الحدود مُفرضة - سرعة خارج نطاق الهدوء تُرفض`() {
        try {
            JarvisVoiceSpec(rateFactor = 1.05f)
            org.junit.Assert.fail("أسرع من الطبيعي يخالف الشخصية الهادئة")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
        try {
            JarvisVoiceSpec(rateFactor = 0.79f)
            org.junit.Assert.fail("0.79 تحت الحد الأدنى")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
    }

    @Test
    fun `الحدود مُفرضة - خشونة مبالغ فيها تُرفض`() {
        try {
            JarvisVoiceSpec(raspIntensity = 0.41f)
            org.junit.Assert.fail("فوق 0.4 = خشونة مفتعلة")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
        try {
            JarvisVoiceSpec(raspIntensity = -0.1f)
            org.junit.Assert.fail("قيمة سالبة غير منطقية")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
    }

    @Test
    fun `الحدود مُفرضة - لغة غير عربية تُرفض`() {
        try {
            JarvisVoiceSpec(languageTag = "en")
            org.junit.Assert.fail("شخصية Jarvis عربية أولاً")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
    }

    @Test
    fun `القيم الحدية الصحيحة تُقبل - عمق التمييز`() {
        // اختبار حدّي واحد لكل قيد يكفي لكشف أي خلل في operator range
        JarvisVoiceSpec(pitchFactor = 0.7f)   // الحد الأدنى تماماً
        JarvisVoiceSpec(pitchFactor = 1.0f)   // الحد الأعلى تماماً
        JarvisVoiceSpec(rateFactor = 0.8f)
        JarvisVoiceSpec(rateFactor = 1.0f)
        JarvisVoiceSpec(raspIntensity = 0f)
        JarvisVoiceSpec(raspIntensity = 0.4f)
    }

    @Test
    fun `وسوم الأسلوب الإلزامية - إسقاط DEEP أو CALM يُرفض`() {
        try {
            JarvisVoiceSpec(styleTags = setOf(VoiceStyleTag.CALM, VoiceStyleTag.CONFIDENT))
            org.junit.Assert.fail("بلا DEEP ليست شخصية Jarvis")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
        try {
            JarvisVoiceSpec(styleTags = setOf(VoiceStyleTag.DEEP, VoiceStyleTag.CONFIDENT))
            org.junit.Assert.fail("بلا CALM ليست شخصية Jarvis")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
    }
}
