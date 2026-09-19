package com.jarvis.mobile.verification

/**
 * محرك التحقق (Phase 11 — SPEC.md: قبل أي قدرة تنفيذية، وبعد بوابة المخاطرة 10).
 *
 * العقد:
 * 1. لكل خطوة Verification Contract: ماذا كان يجب أن يحدث؟ وما الدليل القابل للرصد الذي يثبته؟
 * 2. مصادر الإثبات (Evidence): حالة الملف / نتيجة الخطوة / حدث النظام / قراءة الشاشة.
 * 3. لا نجاح كاذب: SUCCESS لا يُعاد أبداً إلا إذا استوفى الدليل كل بنود العقد —
 *    دليل ناقص → Inconclusive، دليل غير مستوفى → Failure، وليس SUCCESS أبداً.
 * 4. المحرك نقي وحتمي: نفس المدخلات → نفس النتيجة، بلا زمن ولا عشوائية ولا اعتماد على Android —
 *    لذلك كل قراره مختبر ب اختبارات وحدة صرفة.
 *
 * الربط بآلة الحالة: المتصل يقود الآلة إلى VERIFYING (AgentEvent.VERIFY) ثم يستدعي
 * [VerificationEngine.verify] — النتيجة هي وحدها ما يبرر SUCCESS (→ DONE) أو FAIL (→ ERROR).
 */
class VerificationEngine {

    /**
     * عقد التحقق لخطوة واحدة: ماذا كان يجب أن يحدث، وبأي دليل قابل للرصد يُثبت.
     */
    data class VerificationContract(
        val stepId: String,
        val expectation: String,
        val evidenceRequired: List<String>,
    ) {
        init {
            require(stepId.isNotBlank()) { "عقد تحقق بلا معرّف خطوة مرفوض" }
            require(expectation.isNotBlank()) { "عقد تحقق بلا توقع مرفوض: ماذا كان يجب أن يحدث؟" }
            require(evidenceRequired.isNotEmpty()) {
                "عقد تحقق بلا دليل مطلوب مرفوض: لا يمكن التحقق بلا دليل قابل للرصد"
            }
            require(evidenceRequired.all { it.isNotBlank() }) { "بنود الدليل يجب أن تكون غير فارغة" }
        }
    }

    /** دليل قابل للرصد يجيب بنداً واحداً من بنود العقد. */
    sealed class Evidence {
        /** البند من evidenceRequired الذي يجيبه هذا الدليل. */
        abstract val requirement: String

        /** هل يستوفي هذا الدليل ما طُلب منه؟ */
        abstract val satisfied: Boolean

        /** دليل حالة ملف: الوجود والحجم غير الصفري (إن حُدد). */
        data class FileEvidence(
            override val requirement: String,
            val path: String,
            val exists: Boolean,
            val sizeBytes: Long? = null,
        ) : Evidence() {
            override val satisfied: Boolean
                get() = path.isNotBlank() && exists && (sizeBytes == null || sizeBytes > 0)
        }

        /** دليل نتيجة الخطوة نفسها: وصف فعلي لما حدث + نجاح/فشل. */
        data class ResultEvidence(
            override val requirement: String,
            val detail: String,
            val success: Boolean,
        ) : Evidence() {
            override val satisfied: Boolean
                get() = success && detail.isNotBlank()
        }

        /** دليل حدث نظام: حدث مسمّى رُصد فعلاً أم لا. */
        data class SystemEventEvidence(
            override val requirement: String,
            val event: String,
            val observed: Boolean,
        ) : Evidence() {
            override val satisfied: Boolean
                get() = event.isNotBlank() && observed
        }

        /** دليل قراءة الشاشة: وصف ما رُصد ومطابقته للمتوقع. */
        data class ScreenEvidence(
            override val requirement: String,
            val description: String,
            val matches: Boolean,
        ) : Evidence() {
            override val satisfied: Boolean
                get() = matches && description.isNotBlank()
        }
    }

    /** نتيجة التحقق — ثلاث حالات صريحة، ولا SUCCESS إلا بدليل كامل مستوفٍ. */
    sealed class VerificationResult {
        abstract val stepId: String
        abstract val evidence: List<Evidence>

        data class Success(
            override val stepId: String,
            override val evidence: List<Evidence>,
        ) : VerificationResult()

        data class Failure(
            override val stepId: String,
            val reason: String,
            override val evidence: List<Evidence>,
        ) : VerificationResult()

        /** دليل ناقص — لا يمكن الحكم نجاحاً ولا فشلاً، والأكيد أنه ليس SUCCESS. */
        data class Inconclusive(
            override val stepId: String,
            val missing: List<String>,
            override val evidence: List<Evidence>,
        ) : VerificationResult()

        val isSuccess: Boolean get() = this is Success
    }

    /**
     * التحقق الفعلي: مقارنة الدليل المقدَّم بعقد الخطوة.
     *
     * - أي دليل غير مستوفى → Failure (فشل حقيقي بدليل).
     * - بنود بلا دليل يجيبها → Inconclusive (دليل ناقص).
     * - كل البنود مُجابة وكل الدليل مستوفى → Success (نجاح حقيقي).
     *
     * @throws IllegalArgumentException إن جاء دليل يجيب بنداً غير معلن في العقد —
     *         دليل خارج العقد لا معنى للتدقيق به.
     */
    fun verify(contract: VerificationContract, evidence: List<Evidence>): VerificationResult {
        val foreign = evidence.filter { it.requirement !in contract.evidenceRequired }
        require(foreign.isEmpty()) {
            "دليل خارج بنود العقد مرفوض: ${foreign.map { it.requirement }}"
        }

        val unsatisfied = evidence.filter { !it.satisfied }
        if (unsatisfied.isNotEmpty()) {
            return VerificationResult.Failure(
                contract.stepId,
                "دليل غير مستوفى: ${unsatisfied.map { it.requirement }}",
                evidence,
            )
        }

        val covered = evidence.map { it.requirement }.toSet()
        val missing = contract.evidenceRequired.filter { it !in covered }
        if (missing.isNotEmpty()) {
            return VerificationResult.Inconclusive(contract.stepId, missing, evidence)
        }

        return VerificationResult.Success(contract.stepId, evidence)
    }
}
