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
Tool Selector (Scoring)   (Phase 10) → relevance, availability, permission, cost, latency, reliability, risk
   ↓
Execution Engine          (Phase 10-17)
   ↓
Observation               (Phase 19)
   ↓
Verification              (Phase 19) → What should have happened? What happened? Observable evidence?
   ↓
Recovery (MAX_RETRIES=3)  (Phase 20)
   ↓
Final Response (عربي)     (Phase 6)
   ↓
TTS                       (Phase 7)
```

حلقة المهمة إلزامية: UNDERSTAND → PLAN → EXECUTE → OBSERVE → VERIFY →
(SUCCESS → الخطوة التالية | NO → DIAGNOSE → RECOVER → VERIFY مجدداً | SAFE FAILURE).

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
  tools/           Tool Registry + كل أداة + Scoring         [Phase 10]
  android/         Intents + Clipboard + Share               [Phase 11]
  accessibility/   AccessibilityService controller           [Phase 12]
  notifications/   NotificationListener                      [Phase 13]
  files/           قراءة/إنشاء المستندات                     [Phase 14]
  browser/         Web Research + Browser Agent              [Phase 15-16]
  code/            Sandbox تنفيذ الكود                       [Phase 17]
  memory/          Short-Term/Task/Long-Term/Tool Memory     [Phase 18]
  verification/    Verification Engine + Verification Contract [Phase 19]
  security/        Risk + Confirmation Layer                 [Phase 21]
  permissions/     Permission Manager                        [Phase 3+]
  diagnostics/     Developer Mode + Telemetry محلية          [Phase 58, 20]
```

## 5. آلة الحالة
- المصدر: `core/JarvisStateMachine.kt` — جدول انتقالات صريح، أي حدث غير معرّف يُرفض ويسجَّل.
- كل انتقال له اختبار وحدة (`JarvisStateMachineTest`) يشمل المسارات الإلزامية:
  الحلقة الكاملة، Barge-in، مسار التأكيد، مسار التعافي من الفشل.
- UI يستمع للـ StateFlow فقط — لا حالة UI منفصلة عن حالة النظام.

## 6. الواجهة
- خلفية `#050A14`، Orb مركزي 260dp بثلاث طبقات متحركة (هالة، نواة، قوسان دوّاران).
- إيقاع الحركة (سرعة دوران/نبض/لون) مشتق من الحالة الفعلية — لا أنيميشن تجميلي منفصل عن النظام.

## 7. الاختبارات
- وحدة: آلة الحالة (9 اختبارات)، عقد المهمة (3 اختبارات) — `./gradlew testDebugUnitTest`.
- Integration لاحقاً: Voice → STT → Agent → Tool → TTS (Phase 8+).
- JARVIS_REAL_WORLD_TESTS (TEST 001-010) تُنشأ مع Phase 9.
- Definition of Done: ALL REQUIRED TESTS = PASS على جهاز حقيقي قبل أي APPROVED.

## 8. CI/CD
GitHub Actions (`.github/workflows/build.yml`): testDebugUnitTest → assembleDebug → artifact.
المسار المستقبلي: release APK موقّع بمفتاح CI secrets (Phase 25).

## 9. حدود معروفة (لا نتظاهر بعكسها)
- الواجهة الحالية تعرض الحالة فقط؛ لا صوت ولا مهام بعد — موثقة NOT IMPLEMENTED في README/FEATURE_STATUS.
- "إغلاق" لوحة المهام زر فعلي؛ كل عنصر UI لاحق يجب أن يعمل فعلياً أو لا يُعرض.
