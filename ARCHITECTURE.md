# Jarvis Mobile — Architecture

وثيقة المعمارية (Phase 0). تُحدَّث مع كل Phase وتسبق أي تنفيذ جديد.

## 1. المبدأ الحاكم
Reliability → Actual task execution → Arabic understanding → Verification → Voice quality →
Real Android control → Speed → UI polish (القسم 68 — بهذا الترتيب، بلا استثناء).
**توضيح (2026-09-19):** أولوية أقل في الترتيب لا تعني أقل في الجودة — "Voice quality" له مواصفة
إلزامية خاصة (§3.1) يُقاس عليها TTS عند بنائه؛ الترتيب يعني توقيت البناء فقط.

## 2. المعمارية العامة

```
User (عربي: صوت أو كتابة)
   ↓
Voice/Text Input          (Phase 3-4, 8)
   ↓
Intent + Context          (Phase 5-6)
   ↓
Task Planner ✓            (Phase 9)  → Task Contract + TaskPlan (خطوات بعقود تحقق جاهزة)
   ↓
Risk/Confirmation Engine ✓ (Phase 10) → تصنيف خطورة كل خطوة → قرار تأكيد، بوابة WAITING_CONFIRMATION
   ↓
Verification Engine ✓     (Phase 11) → معايير تحقق قابلة للقياس لكل خطوة قبل أي قدرة تنفيذ
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
TTS — طبقات صوتية         (Phase 7)  → Neural أساسي → عربي فاخم → System fallback (ADR-7/11)
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
| ADR-7 (منقح 2026-09-19) | TTS بطبقات: **Neural TTS أساسي** (sherpa-onnx، Apache-2.0، offline) + System TTS **fallback فقط** | مجرد System TTS كخيار رئيسي | الصوت جزء من هوية Jarvis وليس وسيلة إيصال فقط: مواصفة صوتية إلزامية (عربي رجولي، عميق، فخم هادئ واثق، نطق عربي ممتاز، غير روبوتي) لا يحققها System TTS الافتراضي عادة. sherpa-onnx يعمل offline على aarch64 بزمن استجابة عملي (خلافاً لـ XTTS-v2 المرفوض سابقاً لأنه ثقيل جداً). System TTS يبقى Fallback عند غياب النموذج/تعطله |
| ADR-8 | بدون Root، Android APIs رسمية فقط | حلول Root/ADB | القسم 69 |
| ADR-9 | Zero paid dependency | أي خدمة مدفوعة | القسم 16 — كل تبعية موثقة في DEPENDENCIES.md |
| ADR-10 | محرك المخاطرة والتأكيد (10) ومحرك التحقق (11) **قبل** أي فيز قدرة تنفيذية (12-19) | بناء القدرات أولاً وإضافة شبكة الأمان لاحقاً | القدرات (تنفيذ إجراءات، ملفات، تصفح، كود) خطيرة ولا يُصح بناؤها واختبارها بلا بوابة تحقق/تأكيد جاهزة، حتى لو كانت معطّلة افتراضياً — قرار 2026-09-19 بإعادة ترتيب الخطة |
| ADR-11 | طبقات صوتية TTSProvider: Neural أساسي → محرك TTS عربي أعلى جودة (سحابي/عصبي) → System TTS fallback | System TTS وحده | جودة الصوت متطلب معماري لا اختيارياً (انظر المواصفة الصوتية أدناه) — الفشل الصوتي يهبط للطبقة الأدنى بلا انهيار (SAFE FAILURE) |

## 3.1 المواصفة الصوتية الرسمية لـ Jarvis (ADR-7/ADR-11 — إلزامية، 2026-09-19)

**الصوت جزء من هوية Jarvis، ليس وسيلة إيصال فقط.** أي تنفيذ TTS لا يحقق الأهداف أدناه لا يُعتد به.

### الأهداف الصوتية الإلزامية
- عربي **رجولي**، **عميق ومنخفض نسبياً** (low-pitch).
- **خشن قليلاً بصورة طبيعية** (subtle natural rasp) — بلا مبالغة ولا تشويه.
- **فخم، قوي، هادئ، واثق** — إيقاع متزن غير متعجل.
- **طبيعي غير روبوتي** — بلا طنين أو نبرة آلية مسطّحة.
- **نطق عربي واضح وممتاز** مع وقفات وإيقاع كلام طبيعيين.
- ملائم لشخصية مساعد شخصي متقدم (Jarvis).

### طبقات الصوت (Tiers)
| Tier | المصدر | الدور |
|------|--------|-------|
| 1 | **Neural TTS محلي** (sherpa-onnx/Apache-2.0، offline) | الأساسي — صوت عربي رجولي مُنتقى (مثل أصوات Piper العربية) مع ضبط pitch/rate ضمن حدود الشخصية |
| 2 | **محرك TTS عربي أعلى جودة** (سحابي/عصبي عند توفره، ضمن قاعدة ADR-9) | يُقيَّم ويُضيف كـ Provider عند الحاجة — لا يُغني عن Tier 1 كأساس offline |
| 3 | **Android System TTS** | **Fallback فقط** — عند غياب نموذج Tier 1 أو فشله. استخدام System TTS كخيار رئيسي مخالف للمواصفة |

### عقد TTSProvider (Phase 7)
- الواجهة واحدة، التنفيذ طُبقات مع fallback تلقائي آمن: Tier 1 ← Tier 2 (إن وُجد) ← Tier 3.
- `JarvisVoiceSpec` (واصف الشخصية الصوتية: جنس، نطاق pitch، سرعة، أسلوب) يُطبَّق على كل tier —
  كل طبقة تبذل جهدها لتحقيقه، وتُوثَّق حدودها بصراحة.
- كل tier قابل للاختبار بعقد: اختيار tier، fallback عند الفشل، تطبيق معاملات الشخصية،
  وعدم انهيار SAFE FAILURE عند تعطل كل الطبقات.
- التحقق الصوتي المسموع على جهاز حقيقي شرط APPROVED النهائي للفيز (قسم 14 من SPEC — جهاز حقيقي).

**القاعدة:** الرفض/التقييم المسبق لمحرك (كما حصل مع XTTS-v2 الثقيل) لا يعني الاقتصار على أسوأ
متاح — المعيار هو المواصفة أعلاه، والمحركات تُقيَّم ضدها.

## 4. بنية الحزم (تتوسع مع الـ Phases)

```
com.jarvis.mobile/
  core/            آلة الحالة، Task State، العقود            [Phase 1 ✓]
  ui/              Compose: Orb، الشاشة، اللوحات             [Phase 1-2 ✓]
  llm/             LLMProvider + Gemini + Fallback           [Phase 5]
  voice/           Mic + VAD + جلسة صوتية + Barge-in         [Phase 3, 8, 56]
  stt/             STTProvider + Google/Vosk + اختبار عربي   [Phase 4]
  tts/             TTSProvider (طبقات: Neural أساسي → عربي فاخم → System fallback) +
                   ArabicTextNormalizer + JarvisVoiceSpec (شخصية الصوت) [Phase 7 — البنية ✓
                   بترتيب طبقات مُفرض بنيوياً؛ ربط المحركات الفعلية (sherpa-onnx/System) لاحقاً]
  agent/           TaskPlanner + TaskPlan + عقد المهمة       [Phase 9 ✓]
  security/        RiskEngine + بوابة WAITING_CONFIRMATION   [Phase 10 ✓] ← قبل أي قدرة
  verification/    VerificationEngine + عقد التحقق            [Phase 11 ✓] ← قبل أي قدرة
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
- الحالاتان WAITING_CONFIRMATION وVERIFYING موجودتان منذ Phase 1 — ومحرك المخاطرة (Phase 10،
  `security/RiskEngine`) ومحرك التحقق (Phase 11، `verification/VerificationEngine`) مبنيان فوق هذا
  الأساس: بوابة 10 تقود الآلة فعلياً عبر WAITING_CONFIRMATION (لا مسار جانبي إلى EXECUTING)،
  ونتيجة التحقق من 11 هي وحدها ما يبرر SUCCESS أو FAIL.

## 6. الواجهة
- خلفية `#050A14`، Orb مركزي 260dp بثلاث طبقات متحركة (هالة، نواة، قوسان دوّاران).
- إيقاع الحركة (سرعة دوران/نبض/لون) مشتق من الحالة الفعلية — لا أنيميشن تجميلي منفصل عن النظام.
- كل الحالات الـ 11 لها إعداد بصري خاص في `Orb.kt` (التحقق عبر AgentStateContractTest).

## 7. الاختبارات
- وحدة: آلة الحالة (10 اختبارات)، عقد الحالات (4)، عقد المهمة (3)، عقد محرك المخاطرة والتأكيد
  (13)، عقد محرك التحقق (13)، مخطط المهام (13)، سجل الأدوات (17)، الصوت: شخصية (7) +
  تطبيع عربي (18) + سلسلة الطبقات (11) = **109 اختبار `@Test`** —
  `./gradlew testDebugUnitTest`. (عدّاد CI هو المرجع المعتمد عند أي خلاف مع الوثائق؛
  تحقق محلي بديل عند غياب SDK: kotlinc + JUnit مباشرة — أُنجز 2026-09-19 بنتيجة 109/109.)
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
