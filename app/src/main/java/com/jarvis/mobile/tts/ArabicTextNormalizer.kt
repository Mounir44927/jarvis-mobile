package com.jarvis.mobile.tts

/**
 * تطبيع النص العربي للنطق (Phase 7 — SPEC): أرقام، عملات، رموز، اختصارات، ترقيم.
 *
 * العقد:
 * 1. حتمي بالكامل: نفس الإدخال → نفس الإخراج دائماً.
 * 2. SAFE FAILURE: أي فشل داخلي غير متوقع يعيد الإدخال كما هو — التطبيع يحسّن ولا يُكسر أبداً
 *    (صوت Jarvis يجب أن يعمل حتى لو فشل التطبيع).
 * 3. لا يلمس ما لا يفهمه: الكلمات والجُمل تمر كما هي — يُحوِّل الرموز/الأرقام فقط إلى نطق عربي.
 */
class ArabicTextNormalizer {

    /** خطوات التطبيع بالترتيب — كل خطوة دالة نصية نقية. */
    private val steps: List<Pair<String, (String) -> String>> = listOf(
        "التحوّل الهجائي-العربي" to ::latinizeForeignWords,
        "الاختصارات" to ::expandAbbreviations,
        "العملات" to ::normalizeCurrencies,
        "الرموز والعمليات" to ::normalizeSymbols,
        "النِسب المئوية" to ::normalizePercentages,
        "الأرقام العربية الشرقية" to ::normalizeEasternDigits,
        "قراءة الأرقام" to ::spellNumbers,
        "تنظيف الترقيم" to ::normalizePunctuation,
        "إزالة التشكيل" to ::stripDiacritics,
    )

    /** تطبيع نص للنطق — حتمي، وآمن الفشل (يعيد الأصل عند أي استثناء غير متوقع). */
    fun normalize(input: String): String {
        if (input.isBlank()) return input
        return try {
            var text = input.trim()
            for ((name, step) in steps) {
                text = try {
                    step(text)
                } catch (e: Exception) {
                    // خطوة فشلت → نتجاوزها ولا نكسر السلسلة (SAFE FAILURE لكل خطوة)
                    text
                }
            }
            text
        } catch (e: Exception) {
            input
        }
    }

    // ------------------------------------------------------------------
    // 1) تحويل الكلمات اللاتينية الشائعة إلى نطق عربي مكتوب
    // ------------------------------------------------------------------

    private val foreignWords = mapOf(
        "jarvis" to "جارفيس",
        "ok" to "حسناً",
        "okay" to "حسناً",
        "wifi" to "واي فاي",
        "app" to "تطبيق",
        "sms" to "رسالة نصية",
        "url" to "رابط",
        "pdf" to "بي دي إف",
        "google" to "غوغل",
        "android" to "أندرويد",
        "email" to "بريد إلكتروني",
    )

    private fun latinizeForeignWords(text: String): String {
        var result = text
        for ((latin, arabic) in foreignWords.entries.sortedByDescending { it.key.length }) {
            // حدود حروف لاتينية (لا \b) — ليتعرف "الwifi" الملتصقة بكلمة عربية
            result = result.replace(Regex("(?i)(?<![A-Za-z])${Regex.escape(latin)}(?![A-Za-z])"), arabic)
        }
        return result
    }

    // ------------------------------------------------------------------
    // 2) الاختصارات العربية واللاتينية
    // ------------------------------------------------------------------

    private val abbreviations = mapOf(
        "كم" to "كيلومتر",
        "كغ" to "كيلوغرام",
        "هـ" to "هجري",
    )

    private fun expandAbbreviations(text: String): String {
        var result = text
        // اختصارات بعلامة ترقيم تُستبدل أولاً (أدق)
        for ((short, full) in listOf("د." to "الدكتور ", "أ.د" to "الأستاذ الدكتور ")) {
            result = result.replace(short, full)
        }
        // اختصارات كلمات كاملة بحدود كلمة
        for ((short, full) in mapOf("كم" to "كيلومتر", "كغ" to "كيلوغرام", "هـ" to "هجري")) {
            result = result.replace(Regex("(?<![\\p{L}])${Regex.escape(short)}(?![\\p{L}])"), full)
        }
        return result
    }

    // ------------------------------------------------------------------
    // 3) العملات
    // ------------------------------------------------------------------

    private fun normalizeCurrencies(text: String): String {
        var result = text
        // $100 أو 100$ → مئة دولار (الرقم يُنطَل لاحقاً)
        result = result.replace(Regex("\\$\\s*(\\d+(?:[.,]\\d+)?)"), "$1 دولار")
        result = result.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*\\$"), "$1 دولار")
        result = result.replace(Regex("€\\s*(\\d+(?:[.,]\\d+)?)"), "$1 يورو")
        result = result.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*€"), "$1 يورو")
        result = result.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*ج\\.م"), "$1 جنيه مصري")
        result = result.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*ر\\.س"), "$1 ريال سعودي")
        result = result.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*د\\.إ"), "$1 درهم إماراتي")
        result = result.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*د\\.ك"), "$1 دينار كويتي")
        return result
    }

    // ------------------------------------------------------------------
    // 4) الرموز والعمليات
    // ------------------------------------------------------------------

    private fun normalizeSymbols(text: String): String {
        var result = text
        result = result.replace(Regex("(\\d+)\\s*\\+\\s*(\\d+)"), "$1 زائد $2")
        result = result.replace(Regex("(\\d+)\\s*-\\s*(\\d+)"), "$1 ناقص $2")
        result = result.replace(Regex("(\\d+)\\s*[×x*]\\s*(\\d+)"), "$1 ضرب $2")
        result = result.replace(Regex("(\\d+)\\s*[÷/]\\s*(\\d+)"), "$1 مقسوماً على $2")
        result = result.replace(Regex("(\\d+)\\s*=\\s*(\\d+)"), "$1 يساوي $2")
        result = result.replace(Regex("\\s*&\\s*"), " و ")
        result = result.replace(Regex("\\s*@\\s*"), " على ")
        return result
    }

    // ------------------------------------------------------------------
    // 5) النسب المئوية
    // ------------------------------------------------------------------

    private fun normalizePercentages(text: String): String {
        return text.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*%"), "$1 بالمئة")
    }

    // ------------------------------------------------------------------
    // 6) الأرقام العربية الشرقية → غربية (قبل قراءة الأرقام)
    // ------------------------------------------------------------------

    private fun normalizeEasternDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when {
                    ch in '٠'..'٩' -> ('0' + (ch - '٠'))  // عربي شرقي → غربي
                    ch in '۰'..'۹' -> ('0' + (ch - '۰'))  // فارسي → غربي
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // 7) قراءة الأرقام بالعربية (صيغة النطق الفصيح المبسّط)
    // ------------------------------------------------------------------

    private val ones = arrayOf(
        "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة",
        "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر",
        "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر",
    )
    private val tens = arrayOf("", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hundreds = arrayOf(
        "", "مئة", "مئتان", "ثلاثمئة", "أربعمئة", "خمسمئة", "ستمئة", "سبعمئة", "ثمانمئة", "تسعمئة",
    )

    /** قراءة عدد غير سالب حتى 999,999,999 — صيغة نطق مبسطة صحيحة فصيحاً. */
    fun spellArabicNumber(number: Long): String {
        require(number >= 0) { "الأرقام السالبة تُعالج في spellNumbers وليس هنا" }
        if (number == 0L) return "صفر"
        val parts = mutableListOf<String>()

        val millions = number / 1_000_000
        val thousands = (number % 1_000_000) / 1_000
        val remainder = number % 1_000

        if (millions > 0) {
            parts.add(when {
                millions == 1L -> "مليون"
                millions == 2L -> "مليونان"
                millions in 3..10 -> "${spellArabicNumber(millions)} ملايين"
                else -> "${spellArabicNumber(millions)} مليوناً"
            })
        }
        if (thousands > 0) {
            parts.add(when {
                thousands == 1L -> "ألف"
                thousands == 2L -> "ألفان"
                thousands in 3..10 -> "${spellArabicNumber(thousands)} آلاف"
                else -> "${spellArabicNumber(thousands)} ألفاً"
            })
        }
        if (remainder > 0) parts.add(spellUnder1000(remainder.toInt()))

        return parts.joinToString(" و")
    }

    private fun spellUnder1000(n: Int): String {
        val parts = mutableListOf<String>()
        val h = n / 100
        val rest = n % 100
        if (h > 0) parts.add(hundreds[h])
        if (rest > 0) {
            parts.add(
                if (rest < 20) ones[rest]
                else {
                    val unit = rest % 10
                    val ten = rest / 10
                    if (unit > 0) "${ones[unit]} و${tens[ten]}" else tens[ten]
                },
            )
        }
        return parts.joinToString(" و")
    }

    private fun spellNumbers(text: String): String {
        return Regex("\\d+(?:[.,]\\d+)?").replace(text) { match ->
            val raw = match.value.replace(",", "")
            val dotIndex = raw.indexOf('.')
            if (dotIndex == -1) {
                val n = raw.toLongOrNull() ?: return@replace match.value
                if (n > 999_999_999L) return@replace match.value // خارج النطاق: يبقى رقماً (لا قراءة خاطئة)
                spellArabicNumber(n)
            } else {
                val intPart = raw.substring(0, dotIndex).toLongOrNull() ?: return@replace match.value
                val fracDigits = raw.substring(dotIndex + 1)
                if (fracDigits.length > 4) return@replace match.value
                val intRead = if (intPart in 0..999_999_999L) spellArabicNumber(intPart) else raw.substring(0, dotIndex)
                val fracRead = fracDigits.map { d -> ones[d - '0'] }
                    .joinToString(" ")
                "$intRead فاصلة $fracRead"
            }
        }
    }

    // ------------------------------------------------------------------
    // 8) تنظيف الترقيم لوقفات طبيعية
    // ------------------------------------------------------------------

    private fun normalizePunctuation(text: String): String {
        var result = text
        // أولاً: توحيد التكرار (قبل إضافة المسافات — وإلا تتباعد النقاط)
        result = result.replace(Regex("\\.{2,}"), ".")      // نقاط متكررة → نقطة واحدة
        result = result.replace(Regex("؟{2,}"), "؟")
        result = result.replace(Regex("!{2,}"), "!")
        // ثم: وقفات موحدة
        result = result.replace(Regex("\\s*،\\s*"), "، ")   // فاصلة عربية
        result = result.replace(Regex("\\s*؛\\s*"), "؛ ")   // فاصلة منقوطة
        result = result.replace(Regex("\\s*\\.\\s*"), ". ") // نقطة: وقفة نهائية
        result = result.replace(Regex("\\s*:\\s*"), ": ")
        return result.trim()
    }

    // ------------------------------------------------------------------
    // 9) إزالة التشكيل (محركات TTS العربية تتعامل معه سيئاً غالباً)
    // ------------------------------------------------------------------

    private fun stripDiacritics(text: String): String {
        // نطاق التشكيل العربي U+064B..U+065F + التطويل لا يُمس (حرف صالح)
        return text.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
    }
}
