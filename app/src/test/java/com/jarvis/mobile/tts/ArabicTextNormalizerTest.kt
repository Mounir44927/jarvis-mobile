package com.jarvis.mobile.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات عقد ArabicTextNormalizer (Phase 7 — SPEC):
 * - جدول إدخال/إخراج لكل حالة تطبيع (أرقام، عملات، رموز، اختصارات، شرقية، ترقيم، تشكيل).
 * - حتمية: نفس الإدخال → نفس الإخراج.
 * - SAFE FAILURE: المدخلات الفارغة/الغريبة لا تكسر التطبيع أبداً.
 */
class ArabicTextNormalizerTest {

    private val normalizer = ArabicTextNormalizer()

    // ------------------------------------------------------------------
    // 1) جدول الإدخال/الإخراج — كل حالة تطبيع
    // ------------------------------------------------------------------

    @Test
    fun `جدول التطبيع - نص نظيف يمر بلا تغيير جوهري`() {
        assertEquals("المهمة اكتملت بنجاح.", normalizer.normalize("المهمة اكتملت بنجاح."))
    }

    @Test
    fun `جدول التطبيع - الأرقام الصحيحة تُقرأ عربيا`() {
        assertEquals("لديك ثلاثة رسائل جديدة", normalizer.normalize("لديك 3 رسائل جديدة"))
        assertEquals("حصلت على مئة نقطة", normalizer.normalize("حصلت على 100 نقطة"))
        assertEquals("انتهى الأمر بعد سبعة أيام", normalizer.normalize("انتهى الأمر بعد 7 أيام"))
    }

    @Test
    fun `جدول التطبيع - الأرقام العشرية تُقرأ بفاصلة`() {
        assertEquals(
            "درجة الحرارة خمسة وعشرون فاصلة خمسة درجة",
            normalizer.normalize("درجة الحرارة 25.5 درجة"),
        )
    }

    @Test
    fun `جدول التطبيع - الأرقام الشرقية تتحول للغربية ثم تُقرأ`() {
        assertEquals("سبعة أسرار للإنتاجية", normalizer.normalize("٧ أسرار للإنتاجية"))
    }

    @Test
    fun `جدول التطبيع - العملة الدولارية تُنطق`() {
        assertEquals("السعر خمسون دولار", normalizer.normalize("السعر $50"))
        assertEquals("دفعنا ثلاثمئة دولار", normalizer.normalize("دفعنا 300$"))
    }

    @Test
    fun `جدول التطبيع - النسبة المئوية تُنطق`() {
        assertEquals("البطارية خمسة وثمانون بالمئة", normalizer.normalize("البطارية 85%"))
    }

    @Test
    fun `جدول التطبيع - العمليات الحسابية تُنطق`() {
        assertEquals(
            "اثنان زائد اثنان يساوي أربعة",
            normalizer.normalize("2 + 2 = 4"),
        )
    }

    @Test
    fun `جدول التطبيع - اختصار الهجرية يُفك قبل قراءة الرقم`() {
        assertEquals(
            "التاريخ ألف وأربعمئة وسبعة وأربعون هجري",
            normalizer.normalize("التاريخ 1447 هـ"),
        )
    }

    @Test
    fun `جدول التطبيع - الكلمات اللاتينية الشائعة تُنطق عربيا`() {
        assertEquals("يا جارفيس، افتح الواي فاي", normalizer.normalize("يا Jarvis، افتح الwifi"))
    }

    @Test
    fun `جدول التطبيع - الترقيم المتكرر يُنظف لوقفات طبيعية`() {
        assertEquals("ماذا حدث؟", normalizer.normalize("ماذا حدث؟؟؟"))
        assertEquals("انتبه!", normalizer.normalize("انتبه!!!"))
        assertEquals("انتظر.", normalizer.normalize("انتظر..."))
    }

    @Test
    fun `جدول التطبيع - التشكيل يُزال ولا يُمس النص الأساس`() {
        val withTashkeel = "اَلْمُهِمَّةُ كَمُلَتْ"
        val result = normalizer.normalize(withTashkeel)
        assertTrue("لا تشكيل في الناتج: $result", result.none { it.code in 0x64B..0x65F })
        assertEquals("المهمة كملت", result)
    }

    @Test
    fun `جدول التطبيع - همزة الوصل في أربعمئة تُنطق صحيحا`() {
        assertEquals("أربعمئة وخمسون", normalizer.spellArabicNumber(450))
        assertEquals("بطارية أربعمئة وخمسون بالمئة", normalizer.normalize("بطارية 450%"))
    }

    // ------------------------------------------------------------------
    // 2) قراءة الأرقام المركبة (spellArabicNumber مباشرة)
    // ------------------------------------------------------------------

    @Test
    fun `قراءة الأرقام - الحالات الأساسية`() {
        assertEquals("صفر", normalizer.spellArabicNumber(0))
        assertEquals("واحد", normalizer.spellArabicNumber(1))
        assertEquals("أربعة عشر", normalizer.spellArabicNumber(14))
        assertEquals("واحد وعشرون", normalizer.spellArabicNumber(21))
        assertEquals("مئة", normalizer.spellArabicNumber(100))
        assertEquals("مئتان", normalizer.spellArabicNumber(200))
    }

    @Test
    fun `قراءة الأرقام - المركبة والكبيرة`() {
        assertEquals("ثلاثمئة وخمسة عشر", normalizer.spellArabicNumber(315))
        assertEquals("ألف وواحد", normalizer.spellArabicNumber(1001))
        assertEquals("ألفان وخمسمئة", normalizer.spellArabicNumber(2500))
        assertEquals(
            "مليون ومئتان وأربعة وثلاثون ألفاً وخمسمئة وسبعة وستون",
            normalizer.spellArabicNumber(1_234_567),
        )
    }

    @Test
    fun `قراءة الأرقام - خارج النطاق تبقى رقما في النص لا قراءة خاطئة`() {
        val huge = "العدد 123456789012345"
        assertEquals("الرقم الضخم يمر كما هو", huge, normalizer.normalize(huge))
    }

    // ------------------------------------------------------------------
    // 3) الحتمية
    // ------------------------------------------------------------------

    @Test
    fun `حتمية - نفس الإدخال يعطي نفس الإخراج دائما`() {
        val samples = listOf(
            "لديك 3 رسائل و$25 رصيد و85% بطارية",
            "اجتماع الساعة 14.30 مع د. أحمد",
            "٧ خطوات + 3 = 10",
        )
        for (sample in samples) {
            val first = normalizer.normalize(sample)
            val second = normalizer.normalize(sample)
            assertEquals("نفس الإدخال → نفس الإخراج", first, second)
        }
    }

    // ------------------------------------------------------------------
    // 4) SAFE FAILURE — لا انهيار أبداً
    // ------------------------------------------------------------------

    @Test
    fun `safe failure - الفراغات تعود كما هي بنفس المرجع`() {
        assertSame("", normalizer.normalize(""))
        assertSame("   ", normalizer.normalize("   "))
    }

    @Test
    fun `safe failure - رموز غريبة لا تكسر التطبيع`() {
        val weird = "@@@ ### $$$ ^^^"
        val result = normalizer.normalize(weird) // لا يجب أن يرمي استثناء
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `safe failure - نص طويل مختلط يمر بلا استثناء`() {
        val mixed = "يا Jarvis: عندك 3 مهام، رصيد $120.5، بطارية 67%، و1400 كلمة جديدة — جاهز؟"
        val result = normalizer.normalize(mixed)
        assertTrue("الناتج ليس فارغاً", result.isNotBlank())
        assertTrue("لا أرقام لاتينية متبقية للنطق: $result", !Regex("""\d""").containsMatchIn(result))
    }
}
