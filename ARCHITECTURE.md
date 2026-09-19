# Jarvis Mobile — Architecture

وثيقة المعمارية (Phase 0). تُحدَّث مع كل Phase وتسبق أي تنفيذ جديد.

## 1. المبدأ الحاكم
Reliability → Actual task execution → Arabic understanding → Verification → Voice quality →
Real Android control → Speed → UI polish (القسم 68 — بهذا الترتيب، بلا استثناء).

## 2. المعمارية العامة

```
User (عربي: صوت أو كتابة)
   ↓
Voice/Text Input          (Phase 3-4, 8)
   ↓
Intent + Context          (Phase 5-6)
   ↓
Task Planner              (Phase 9)  → Task Contract (هدف/قيود/أدوات/خطورة/معايير نجاح)
   ↓
Risk/Confirmation Engine  (Phase 10) → تصنيف خطورة كل خطوة → قرار تأكيد، بوابة WAITING_CONFIRMATION
   ↓
Verification Engine       (Phase 11) → معايير تحقق قابلة للقياس لكل خطوة قبل أي قدرة تنفيذ
   ↓
Tool Selector (Scoring)   (Phase 12) → relevance, availability, permission, cost, latency, reliability, risk
   ↓
Execution Engine          (Phase 12-19) — الأدوات تُبنى معطّلة افتراضياً ولا تعمل إلا عبر بوابة 10
   ↓
Observation               (عقد المراقبة من Phase 11، يُطبق فعلياً من 12)
   ↓
Verification              (Phase 11) → What should have happened? What happened? Observable evidence?
   ↓
Recovery (MAX_RETRIES=3)  (Phase 21)
   ↓
Final Response (عربي)     (Phase 6)
   ↓
TTS                       (Phase 7)
```

حلقة المهمة إلزامية: UNDERSTAND → PLAN → ASSESS RISK → (CONFIRM إذا لزم) → EXECUTE →
OBSERVE → VERIFY → (SUCCESS → الخطوة التالية | NO → DIAGNOSE → RECOVER → VERIFY مجدداً |
SAFE FAILURE).

**قاعدة الترتيب (ADR-10):** لا يُرفع أي فيز قدرة تنفيذية (12-19: Tool Registry، Intents،
Accessibility، الإشعارات، الملفات، الويب، Browser Agent، Sandbox) إلى BUILD PASS قبل أن يكون
محرك المخاطرة والتأكيد (10) ومحرك التحقق (11) APPROVED بإطار عمل مكتمل + اختبارات عقد ناجحة.
الأدوات الجديدة تُبنى **disabled-by-default**: تسجل نيتها في سجل القرارات ولا تؤثر على الجهاز
إلا عبر بوابة المخاطرة والتأكيد.

## 3. القرارات الهندسية (ADR مختصر)

| # | القرار | البديل المرفوض | السبب |
|---|--------|----------------|-------|
| ADR-1 | Kotlin + Jetpack Compose | XML Views | واجهة Orb متحركة تتطلب Canvas تعريفي وسلاسة أعلى |
| ADR-2 | آلة حالة صريحة بجدول انتقالات | حالة مبعثرة في ViewModels | القسم 53 يمنع الانتقالات غير المنطقية — الجدول يفرض ذلك برمجياً |
| ADR-3 | StateFlow كمصدر وحيد للحقيقة | EventBus | اختبار أسهل، ولا أحداث مفقودة، ويعمل مع Compose مباشرة |
| ADR-4 | GitHub Actions لبناء APK | بناء محلي | بيئة التطوير الحالية Termux على هاتف (aarch64/3.6GB RAM) لا تكفي لأداة Gradle/AGP؛ CI مجاني ضمن الحصة |
| ADR-5 | STT هجين: Google RecognizerIntent/Stream أولاً + Vosk offline لاحقاً | Vosk فقط | العربية في محرك Google أدنى جهازياً، وVosk يضاف كمزوّد بديل (Provider Abstraction) |
| ADR-6 | Gemini عبر REST مباشر في Phase 5 | LangChain4j فوراً | تقليل وزن APK ومخاطر توافق Android؛ LangChain4j (Apache-2.0) يُدخل لاحقاً إذا احتاجته Planner/Tools |
| ADR-7 | TTS: واجهة Provider فوق System TTS أولاً | XTTS مدمج | XTTS-v2 لا يحقق زمن استجابة عملي على هاتف متوسط المواصفات؛ يُقيَّم كـ Provider إضافي لاحقاً |
| ADR-8 | بدون Root، Android APIs رسمية فقط | حلول Root/ADB | القسم 69 |
| ADR-9 | Zero paid dependency | أي خدمة مدفوعة | القسم 16 — كل تبعية موثقة في DEPENDENCIES.md |
| ADR-10 | محرك المخاطرة والتأكيد (10) ومحرك التحقق (11) **قبل** أي فيز قدرة تنفيذية (12-19) | بناء القدرات أولاً وإضافة شبكة الأمان لاحقاً | القدرات (تنفيذ إجراءات، ملفات، تصفح، كود) خطيرة ولا يُصح بناؤها واختبارها بلا بوابة تحقق/تأكيد جاهزة، حتى لو كانت معطّلة افتراضياً — قرار 2026-09-19 بإعادة ترتيب الخطة |

## 4. بنية الحزم (تتوسع مع الـ Phases)

```
com.jarvis.mobile/
  core/            آلة الحالة، Task State، العقود            [Phase 1 ✓]
  ui/              Compose: Orb، الشاشة، اللوحات             [Phase 1-2 ✓]
  llm/             LLMProvider + Gemini + Fallback           [Phase 5]
  voice/           Mic + VAD + جلسة صوتية + Barge-in         [Phase 3, 8, 56]
  stt/             STTProvider + Google/Vosk + اختبار عربي   [Phase 4]
  tts/             TTSProvider + ArabicTextNormalizer        [Phase 7, 61]
  agent/           Planner، Skills، System Prompt            [Phase 9, 34, 62]
  security/        Risk + Confirmation Layer                 [Phase 10] ← قبل أي قدرة
  verification/    Verification Engine + Verification Contract [Phase 11] ← قبل أي قدرة
  tools/           Tool Registry + كل أداة + Scoring         [Phase 12]
  android/         Intents + Clipboard + Share               [Phase 13]
  accessibility/   AccessibilityService controller           [Phase 14]
  notifications/   NotificationListener                      [Phase 15]
  files/           قراءة/إنشاء المستندات                     [Phase 16]
  browser/         Web Research + Browser Agent              [Phase 17-18]
  code/            Sandbox تنفيذ الكود                       [Phase 19]
  memory/          Short-Term/Task/Long-Term/Tool Memory     [Phase 20]
  permissions/     Permission Manager                        [Phase 3+]
  diagnostics/     Developer Mode + Telemetry محلية          [Phase 58, 21]
```

## 5. آلة الحالة
- المصدر: `core/JarvisStateMachine.kt` — جدول انتقالات صريح، أي حدث غير معرّف يُرفض ويسجَّل.
- **11 حالة** (IDLE, LISTENING, TRANSCRIBING, UNDERSTANDING, PLANNING, EXECUTING, VERIFYING,
  SPEAKING, WAITING_CONFIRMATION, ERROR, DONE) — العدد والأسماء مؤكدة بعقد اختباري قابل للتنفيذ
  (`AgentStateContractTest`) لا بوصف نصي؛ أي تعديل مستقبلي على القائمة يفشل البناء حتى تُحدَّث الوثائق.
  "التفكير" مُنمذج بحالتين صريحتين: UNDERSTANDING (فهم) ثم PLANNING (تخطيط).
- كل انتقال له اختبار وحدة (`JarvisStateMachineTest`) يشمل المسارات الإلزامية:
  الحلقة الكاملة، Barge-in، مسار التأكيد، مسار التعافي من الفشل.
- UI يستمع للـ StateFlow فقط — لا حالة UI منفصلة عن حالة النظام.
- الحالاتان WAITING_CONFIRMATION وVERIFYING موجودتان منذ Phase 1 — الأساس الحالاتي لمحركي
  المخاطرة (10) والتحقق (11) جاهز قبل أي قدرة تنفيذية.

## 6. الواجهة
- خلفية `#050A14`، Orb مركزي 260dp بثلاث طبقات متحركة (هالة، نواة، قوسان دوّاران).
- إيقاع الحركة (سرعة دوران/نبض/لون) مشتق من الحالة الفعلية — لا أنيميشن تجميلي منفصل عن النظام.
- كل الحالات الـ 11 لها إعداد بصري خاص في `Orb.kt` (التحقق عبر AgentStateContractTest).

## 7. الاختبارات
- وحدة: آلة الحالة (10 اختبارات)، عقد الحالات (4 اختبارات)، عقد المهمة (3 اختبارات) =
  **17 اختبار `@Test`** — `./gradlew testDebugUnitTest`. (عدّاد CI هو المرجع المعتمد عند أي خلاف مع الوثائق.)
- Integration لاحقاً: Voice → STT → Agent → Risk → Tool → Verify → TTS (يبدأ فعلياً من Phase 12+
  بعد جاهزية بوابة المخاطرة والتحقق).
- JARVIS_REAL_WORLD_TESTS (TEST 001-010) تُنشأ مع Phase 9.
- Definition of Done: ALL REQUIRED TESTS = PASS على جهاز حقيقي قبل أي APPROVED.

## 8. CI/CD
GitHub Actions (`.github/workflows/build.yml`): **بوابة قائمة سماح الصلاحيات** → testDebugUnitTest →
assembleDebug → artifact. البوابة تفشل البناء إذا طلب الـ manifest المدمج أي صلاحية خارج القائمة
المعتمدة في DEPENDENCIES.md (أُضيفت 2026-09-19 بعد تقرير DUMP — انظر DEPENDENCIES.md).
المسار المستقبلي: release APK موقّع بمفتاح CI secrets (Phase 25).

## 9. حدود معروفة (لا نتظاهر بعكسها)
- الواجهة الحالية تعرض الحالة فقط؛ لا صوت ولا مهام بعد — موثقة NOT IMPLEMENTED في README/FEATURE_STATUS.
- "إغلاق" لوحة المهام زر فعلي؛ كل عنصر UI لاحق يجب أن يعمل فعلياً أو لا يُعرض.
- الخطة الكاملة للفيزات (2-25) والترتيب المعتمد: `SPEC.md` — المرجع الوحيد عند أي خلاف في الترقيم.
