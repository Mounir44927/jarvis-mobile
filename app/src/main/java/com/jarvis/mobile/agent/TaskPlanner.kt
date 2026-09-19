package com.jarvis.mobile.agent

import com.jarvis.mobile.core.RiskLevel
import com.jarvis.mobile.verification.VerificationEngine.VerificationContract

/**
 * مخطط المهام (Phase 9 — SPEC.md): يحوّل طلب المستخدم إلى [TaskPlan] — كل خطوة بنية صريحة
 * وعقد تحقق جاهز قبل أي تنفيذ (أي قدرة تنفيذية 12-19 تستهلك هذه الخطة عبر بوابة 10).
 *
 * **حتمي بلا شبكة:** في هذه المرحلة لا يوجد LLM بعد (Phase 5) — التخطيط هنا مطابقة قواعد
 * عربية حتمية (كلمات مفتاحية على مستوى الكلمات + نمط تعبير حسابي). نفس الطلب يعطي نفس
 * الخطة دائماً؛ ومخطط LLM لاحقاً سيُدخل خلف نفس الواجهة.
 *
 * المطابقة على مستوى الكلمات المنفصلة (وليس \b في regex) لأن \b في محرك Java لا يعتبر
 * الحروف العربية أحرف كلمات — والكلمة المفتاحية المطابقة جزئياً تخدع ("كمثرى" ليست "كم").
 *
 * SAFE FAILURE: أي طلب لا تطابقه أي قاعدة يُرفض صراحةً — لا خطة افتراضية ولا تخمين.
 */
class TaskPlanner {

    /** خطأ التخطيط: الطلب غير مدعوم في هذه المرحلة — قرار صريح، لا فشل صامت. */
    class UnsupportedRequestException(val request: String) :
        Exception("طلب غير مدعوم في هذه المرحلة (لا قاعدة تخطيط مطابقة): $request")

    /**
     * قاعدة تخطيط: كلمات مفتاحية (مطابقة تامة أو بادئة ساق) + أنماط اختيارية، وباني الخطوات.
     * أول قاعدة تطابق الطلب تفوز — الترتيب جزء من الحتمية.
     */
    private data class Rule(
        val name: String,
        val exactWords: List<String> = emptyList(),
        val stems: List<String> = emptyList(),
        val regexes: List<Regex> = emptyList(),
        val build: (request: String) -> List<PlannedStep>,
    )

    private val rules = listOf(
        // ------------------------------------------------------------ PDF
        Rule(
            name = "إنشاء PDF من نص",
            exactWords = listOf("pdf"),
            build = { request ->
                listOf(
                    PlannedStep(
                        id = "pdf.resolve-content",
                        description = "تحديد نص المحتوى من الطلب: \"$request\"",
                        tools = listOf("text_resolver"),
                        risk = RiskLevel.LOW,
                        verification = VerificationContract(
                            stepId = "pdf.resolve-content",
                            expectation = "نص محتوى غير فارغ جاهز للتحويل",
                            evidenceRequired = listOf("نتيجة تحديد المحتوى"),
                        ),
                    ),
                    PlannedStep(
                        id = "pdf.generate",
                        description = "توليد ملف PDF من المحتوى وحفظه في مسار المستندات",
                        tools = listOf("pdf_generator"),
                        risk = RiskLevel.MEDIUM, // كتابة ملف في مساحة المستخدم
                        verification = VerificationContract(
                            stepId = "pdf.generate",
                            expectation = "الملف موجود بحجم أكبر من صفر",
                            evidenceRequired = listOf("حالة الملف", "حجم الملف"),
                        ),
                    ),
                )
            },
        ),
        // -------------------------------------------------------- حسابية
        Rule(
            name = "حساب رياضي",
            exactWords = listOf("احسب", "كم"),
            regexes = listOf(Regex("[0-9٠-٩]+\\s*[+\\-*/×÷^]\\s*[0-9٠-٩]+")),
            build = { request ->
                listOf(
                    PlannedStep(
                        id = "calc.evaluate",
                        description = "تقييم التعبير الحسابي: \"$request\"",
                        tools = listOf("calculator"),
                        risk = RiskLevel.LOW, // حساب نقي — لا أثر على الجهاز
                        verification = VerificationContract(
                            stepId = "calc.evaluate",
                            expectation = "نتيجة رقمية صحيحة مطابقة للتعبير",
                            evidenceRequired = listOf("نتيجة الحساب"),
                        ),
                    ),
                )
            },
        ),
        // ---------------------------------------------------- مذكرة محلية
        Rule(
            name = "حفظ مذكرة نصية",
            stems = listOf("مذك"),
            exactWords = listOf("ذكّرني", "ذكرني", "اكتب"),
            build = { request ->
                listOf(
                    PlannedStep(
                        id = "note.compose",
                        description = "تحرير نص المذكرة من الطلب: \"$request\"",
                        tools = listOf("text_resolver"),
                        risk = RiskLevel.LOW,
                        verification = VerificationContract(
                            stepId = "note.compose",
                            expectation = "نص مذكرة غير فارغ",
                            evidenceRequired = listOf("نتيجة تحرير المذكرة"),
                        ),
                    ),
                    PlannedStep(
                        id = "note.save",
                        description = "حفظ المذكرة في ملفات التطبيق الخاصة",
                        tools = listOf("file_writer"),
                        risk = RiskLevel.MEDIUM,
                        verification = VerificationContract(
                            stepId = "note.save",
                            expectation = "ملف المذكرة موجود غير فارغ",
                            evidenceRequired = listOf("حالة الملف", "حجم الملف"),
                        ),
                    ),
                )
            },
        ),
    )

    /**
     * تخطيط الطلب إلى خطة كاملة.
     *
     * @throws UnsupportedRequestException إن لم تطابق أي قاعدة — SAFE FAILURE صريح.
     */
    fun plan(request: String): TaskPlan {
        require(request.isNotBlank()) { "طلب فارغ: لا خطة بلا طلب" }

        val matched = rules.firstOrNull { matches(it, request) }
            ?: throw UnsupportedRequestException(request)

        val steps = matched.build(request.trim())
        return TaskPlan(
            objective = matched.name,
            constraints = listOf(
                "تخطيط حتمي بلا LLM في هذه المرحلة (Phase 9) — نفس الطلب يعطي نفس الخطة",
                "كل خطوة بلا تنفيذ: النية فقط حتى بوابة Phase 10",
            ),
            steps = steps,
        )
    }

    /** هل يمكن تخطيط الطلب؟ (للعرض المسبق في الواجهة بلا استثناءات) */
    fun canPlan(request: String): Boolean =
        request.isNotBlank() && rules.any { matches(it, request) }

    /** مطابقة الطلب لقاعدة: كلمات تامة أو سوق بادئة على مستوى الكلمات المنفصلة، أو نمط regex. */
    private fun matches(rule: Rule, request: String): Boolean {
        val tokens = request.lowercase().split(Regex("[\\s،.؟!,:؛()\\[\\]\"']+")).filter { it.isNotBlank() }
        if (rule.exactWords.any { word -> tokens.any { it == word } }) return true
        if (rule.stems.any { stem -> tokens.any { it.startsWith(stem) } }) return true
        return rule.regexes.any { it.containsMatchIn(request) }
    }
}
