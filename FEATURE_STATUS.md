# FEATURE_STATUS.md — بوابة الجودة (القسم 47)

القاعدة: لا تتحول أي ميزة إلى APPROVED مع فشل أي اختبار أساسي.
الحالات: NOT STARTED → IN PROGRESS → BUILD PASS → TESTED → **APPROVED**.

---

## Phase 0 — التحليل والمعمارية والتبعيات

| البند | الحالة | الدليل |
|-------|--------|--------|
| تحليل البيئة والقيود | ✅ PASS | Termux/aarch64/3.6GB RAM → قرار CI (ADR-4) |
| اختيار المكونات مفتوحة المصدر | ✅ PASS | DEPENDENCIES.md (تراخيص موثقة: Apache-2.0/MIT) |
| المعمارية | ✅ PASS | ARCHITECTURE.md |
| إعادة ترتيب الفيزات: المخاطرة/التحقق قبل أي قدرة تنفيذية | ✅ PASS | ADR-10 + SPEC.md (قرار 2026-09-19) |
| **Status** | **APPROVED** | — |

---

## Phase 1 — Android Shell + UI الأساسية

| البند | الحالة | الدليل |
|-------|--------|--------|
| Build | ✅ **PASS** | CI #4 وCI #7 (على 272559e): `assembleDebug` نجح — APK artifact منشور |
| Unit Tests (آلة الحالة + عقد المهمة + عقد الحالات) | ✅ **PASS** | CI #7 وCI #8: جميعها ناجحة — **17** اختبار في نطاق Phase 1 (10 آلة حالة + 4 عقد حالات + 3 عقد مهمة)، والإجمالي الكلي 73 بعد Phase 12 (CI run 35457862471). العدّاد الصحيح وقت CI #4 كان **13** بعدّ `git show ae2a771` — الرقم "12" الوارد سابقاً كان خطأ وثائقي (انظر سجل الفشل) |
| عقد حالات آلة الحالة (`AgentStateContractTest`) | ✅ PASS | اعتُمد بدليل في CI #7 بعد إصلاح انتقال RESET من IDLE (انظر سجل الفشل) |
| Integration | — NOT STARTED — | لا يوجد شيء للتكامل بعد |
| Real Device | ⏳ PENDING | تثبيت APK artifact من CI واختبار يدوي (القائمة أدناه) |
| Error Handling | ✅ PASS | آلة الحالة ترفض الانتقالات غير المنطقية + UI يعرض آخر رفض |
| Arabic | ✅ PASS | واجهة عربية أصلية RTL، علامات الحالة بالعربية |
| Performance | ⏳ PENDING | قياس على جهاز حقيقي |
| **Status** | **BUILD + TESTS: PASS — ينتظر اختبار الجهاز الحقيقي + CI للاختبارات الجديدة** | APK جاهز: Actions → jarvis-debug-apk |

### قائمة اختبار الجهاز الحقيقي (Phase 1)
- [ ] التثبيت من APK المبني في CI يعمل.
- [ ] التطبيق يفتح بلا انهيار.
- [ ] Orb يتحرك في حالة Idle والتسمية "أنا جاهز."
- [ ] لوحة حالة المهمة تفتح وتُغلق بزرها الفعلي.
- [ ] الرجوع للخلفية ثم العودة يحفظ الحالة.
- [ ] إغلاق التطبيق وإعادة فتحه يعيد الحالة إلى IDLE بلا انهيار.
- [ ] دوران الشاشة لا ينهار التطبيق.
- [ ] وضع البطارية المنخفض/قفل الشاشة لا يسبب انهياراً عند العودة.

---

## تقرير APK المفحوص — التحقق بدليل (2026-09-19)

المصدر: فحص جزئي لـ `jarvis-debug-apk` من آخر بناء CI + مقارنة بالوثائق. النتيجة بعد التحقيق:

| # | الملاحظة المبلغ عنها | نتيجة التحقق (الدليل) | الإجراء |
|---|----------------------|------------------------|---------|
| 1 | "classes.dex يُظهر 6 حالات فقط (IDLE, LISTENING, EXECUTING, SPEAKING, ERROR, SUCCESS) — لا THINKING" | **غير مطابق للكود المصدري.** `AgentState.kt` أنشئ بـ **11 حالة** في commit dcf1475 ولم يُعدَّل قط (`git log --follow`: commit واحد)، ولم يُحذف أو يُعاد تسمية أي حالة بين ae2a771 (مصدر الـ APK) والرأس. الاحتمالات: فحص dex جزئي/أداة أظهرت `values()` جزئياً، أو فحص لـ APK من مصدر آخر. **الدائرة على فحص الـ APK، لا على الكود** | ✅ العدد الفعلي (11) أصبح **مؤكداً بعقد اختباري قابل للتنفيذ** `AgentStateContractTest` (عدد + أسماء + مسار وصول لكل حالة + RESET من كل حالة) — أي انحراف مستقبلي يُكسر البناء. توضيح إضافي: "Thinking" ليست حالة واحدة بل حالتان صريحتان: UNDERSTANDING ثم PLANNING |
| 1-ب | (اكتُشف أثناء التحقق) README ذكر "7 حالات: Idle/Listening/Thinking/Speaking/Executing/Error/Success" و"11 اختبار وحدة" | **كلاهما خطأ وثائقي:** الكود الفعلي 11 حالة، والاختبارات وقت بناء الـ APK كانت 13 (`git show ae2a771` → عدد `@Test` = 10 آلة حالة + 3 عقد) | ✅ صُححت README/ARCHITECTURE (11 حالة، 13→17 اختبار بعد العقد الجديد)، وسُجل في سجل الفشل أدناه |
| 2 | "التطبيق المبني يطلب `android.permission.DUMP` (signature-level) بجانب INTERNET وRECORD_AUDIO" | **غير موجود في مصادر هذا المستودع:** مانيفستنا يعلن INTERNET وRECORD_AUDIO فقط (سطرا 4 و7 — وكل تاريخه بلا تعديل)، وفحصتُ AAR كل تبعيات التشغيل فعلياً من Google Maven (core/activity/activity-compose/lifecycle-runtime(-ktx)/compose ui/ui-graphics/ui-tooling/foundation/material3/runtime بالإصدارات المقابلة لـ BOM 2024.12.01): **ولا واحدة تدمج DUMP** (الوحيدة التي تضيف صلاحية: core-ktx تضيف `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — signature-level يعرّفه التطبيق نفسه وغير حساسة). الاحتمالات: فحص على APK من مصدر/نسخة أخرى، أداة تقرير خاطئة، أو نسخة أقدم قُرئت من جهاز به تطبيقات أخرى | 🔒 **إجراء وقائي دائم:** بوابة CI جديدة (قائمة سماح صلاحيات) تفشل أي بناء يعلن صلاحية خارج القائمة المعتمدة في DEPENDENCIES.md — أي اندماج صلاحية من تبعية مستقبلية لن يمر بصمت. راجع أي APK مستقبلاً بـ `aapt dump permissions jarvis-debug-apk` أو `apkanalyzer manifest print` (لا dex) |

---

## خطة الفيزات المعتمدة (المرجع: SPEC.md — الترتيب الجديد ADR-10)

الترتيب الجديد: محرك المخاطرة والتأكيد (10) ومحرك التحقق (11) **قبل** أي فيز قدرة تنفيذية (12-19).
الأدوات من 12 فما فوق تُبنى disabled-by-default ولا تعمل إلا عبر بوابة 10.

| Phase | الموضوع | الحالة |
|-------|---------|--------|
| 2 | تعميق Orb بالصوت الفعلي | NOT STARTED |
| 3 | الميكروفون + الصلاحيات | NOT STARTED |
| 4 | STT | NOT STARTED |
| 5 | LLM/Gemini Provider | NOT STARTED |
| 6 | الرد العربي | NOT STARTED |
| 7 | TTS + ArabicTextNormalizer | **ربط فعلي مكتمل (Phase 7 ربط UI)** — `tts/`: **SherpaOnnxTtsProvider** (NEURAL أساسي: sherpa-onnx v1.13.8 AAR محلي موثّق + Piper ar_JO-kareem-medium-int8 مضمّن في assets + تشغيل AudioTrack حقيقي — لا صوت كاذب: فشل التهيئة/التوليد الفارغ → NotSupported للفولباك) + **AndroidSystemTtsProvider** (SYSTEM — fallback أخير فقط) + **JarvisVoiceSession** (النقطة الوحيدة للنطق: تحية عربية أولى "أهلاً بك. أنا جارفيس، في خدمتك." بمرة واحدة على مستوى العملية — حاجز AtomicBoolean يمنع التكرار من recomposition/lifecycle + إخبار آلة الحالة SPEAKING→RESET + تنفيذ خارج Main thread) + ربط JarvisApp/MainActivity/JarvisScreen (لوحة التشخيص تعرض الطبقة المستخدمة فعلياً). **إكمال الربط (2026-09-20، تحقق بالقراءة من مصدر sherpa-onnx v1.13.8 + `javap` على الـAAR المحلي):** (أ) `EspeakDataInstaller` جديد ينسخ `espeak-ng-data` من assets إلى `filesDir` ويمرّر **مساراً مطلقاً** في `dataDir` — لأن espeak-ng يقرأ بـ`fopen` من نظام ملفات حقيقي، و`InitEspeak` في `piper-phonemize-lexicon.cc` يُنهي العملية (`SHERPA_ONNX_EXIT(-1)`) إن لم يكن المسار صالحاً (النسخ idempotent: علامة + `phontab`، 355 ملفاً/18MB مرة واحدة). (ب) تحقق مسبق من الأصول (موديل/tokens/مجلد espeak) قبل الدخول للأصلي — الأصل المفقود يصبح fallback نظيفاً بدل إنهاء عملية. (ج) **`TtsFallbackChain` يهيّئ الطبقات تلقائياً** (تهيئة متأخرة + عزل فشل = NotSupported ثم السقوط): `prepare()` لم يكن يُستدعى في أي مسار إنتاجي قبل ذلك. (د) تصحيح دلالة السرعة: `rateFactor` تُمرَّر كما هي (كان مقلوبها يجعل الإيقاع أسرع من الطبيعي مخالفاً §3.1) وإزالة `lengthScale` المتضارب. اختبارات جديدة: `EspeakDataInstallerTest` + اختبارا تهيئة في `TtsFallbackChainTest`. **متبقٍ للاعتماد النهائي:** تشغيل على جهاز Android حقيقي + سماع التحية فعلياً + تقييم الجودة ضد §3.1 + إثبات fallback عند فشل Sherpa (CI القادم هو فحص البناء — لا SDK محلي) |
| 8 | حلقة الصوت الكاملة | NOT STARTED |
| 9 | Task Planner + Task Contract | ✅ APPROVED — `agent/TaskPlanner` + `TaskPlan` + 13 اختبار، CI #12 أخضر (56 اختبار) |
| **10** | **محرك المخاطرة والتأكيد (Risk/Confirmation Engine)** | ✅ APPROVED — `security/RiskEngine` + 13 اختبار عقد، CI #8 أخضر (تعريف APPROVED لإطار 10/11 في SPEC: إطار مكتمل + اختبارات عقد ناجحة) |
| **11** | **محرك التحقق (Verification Engine)** | ✅ APPROVED — `verification/VerificationEngine` + 13 اختبار عقد، CI #8 أخضر |
| 12 | Tool Registry + Scoring | ✅ APPROVED — `tools/ToolRegistry` + 17 اختبار عقد (disabled-by-default، تفعيل حصري عبر بوابة 10، رفض CRITICAL، scoring حتمي، أداة معطلة لا تُستدعى أبداً). **التحقق المزدوج:** محلي kotlinc+JUnit 73/73 + CI أخضر (run 35457862471: بوابة الصلاحيات + testDebugUnitTest + APK) — إصلاح عقد أثناء الكتابة: إغلاق مسار جانبي كان يسمح بتفعيل أداة بعد رفض المستخدم في البوابة |
| 13 | Android Intents | NOT STARTED |
| 14 | Accessibility | NOT STARTED |
| 15 | الإشعارات | NOT STARTED |
| 16 | الملفات | NOT STARTED |
| 17 | البحث في الويب | NOT STARTED |
| 18 | Browser Agent | NOT STARTED |
| 19 | Sandbox تنفيذ الكود | NOT STARTED |
| 20 | الذاكرة | NOT STARTED |
| 21 | Self-Healing + Recovery | NOT STARTED |
| 22 | الأداء | NOT STARTED |
| 23 | جهاز حقيقي شامل | NOT STARTED |
| 24 | Regression | NOT STARTED |
| 25 | Release | NOT STARTED |

قاعدة البوابة: لا ينتقل أي فيز من 12-19 إلى BUILD PASS قبل APPROVED للفيزين 10 و11
(إطار عمل مكتمل + اختبارات عقد ناجحة). كل Phase لن تظهر هنا إلا بجدول اختبار كامل وحالة فعلية.

---

## سجل الفشل والإصلاح (مطلوب — القسم 12)

| التاريخ | ما فشل | كيف أُصلح | إعادة الاختبار |
|---------|--------|-----------|----------------|
| 2026-09-20 | **الربط الصوتي لم يكن ينطق أبداً على جهاز حقيقي:** `TtsProvider.prepare()` **لا يُستدعى في أي مسار إنتاجي** (لا السلسلة ولا الجلسة) — وطبقا لعقد المزودين: sherpa يبقى بلا محرك → NEURAL دائماً `NotSupported`، ومزود النظام يرمي "المحرك غير مهيأ" → `SafeFailure` بلا صوت. لم يكتشفه أي اختبار سابق لأن الاختبارات الوهمية تُهيّئ نفسها يدوياً | التهيئة صارت مسؤولية `TtsFallbackChain` بنيوياً: `ensurePrepared()` تهيّئ الطبقة **عند أول نطق فعلي لها فقط** (SYSTEM لا يُلمس عند نجاح NEURAL)، وتفشل التهيئة → تُسجَّل كعدم دعم في هذا الطلب وتهبط للتالية؛ النجاح يُثبَّت (لا إعادة تحميل ثقيل) والفشل لا يُثبَّت (تعافٍ من فشل عابر) | اختبارا عقد جديدان في `TtsFallbackChainTest` + CI القادم |
| 2026-09-20 | **خطر إنهاء عملية فوري عند أول نطق:** كان `dataDir` يُمرَّر كمسار assets (`tts/.../espeak-ng-data`). بقراءة مصدر v1.13.8: `piper-phonemize-lexicon.cc::InitEspeak()` → `espeak_Initialize(data_dir)` وقراءة ملفاته بـ`fopen` من نظام ملفات حقيقي (لا assets)، وإن لم يكن المسار مطلقاً صالحاً يُسجّل "You need to follow our examples to copy the espeak-ng-data directory..." ثم `SHERPA_ONNX_EXIT(-1)` — أي **إنهاء العملية** لا استثناء يُلتقط. كذلك فشل قراءة أصل (`file-utils.cc::ReadFile(AAssetManager*)`) ينتهي بنفس الإنهاء، وليس بـ`NotSupported` كما كان موثّقاً | (أ) `EspeakDataInstaller` ينسخ `espeak-ng-data` من assets إلى `filesDir/jarvis-tts/` (355 ملفاً ~18MB مرة واحدة، idempotent بعلامة + ملف `phontab`، والعلامة تُكتب **بعد** اكتمال النسخ خارج مجلد البيانات) ويُمرَّر **مسار مطلق** في `dataDir`. (ب) تحقق مسبق في المزود: وجود الموديل/tokens/مجلد espeak قبل إنشاء `OfflineTts` — الغياب يُصبح استثناءً يُعزله الفولباك. (ج) تصحيح التوثيق: الأصول تُقرأ أصلياً بـ`AASSET_MODE_BUFFER` لذا تبقى `noCompress: onnx/txt` إلزامية، والنسخ عبر `AssetManager.open()` يعمل مع الضغط بلا اعتماد عليه | `EspeakDataInstallerTest` (نسخ تكراري + idempotency + نسخة ناقصة تُعاد) + CI القادم + تشغيل جهاز حقيقي (معلّق) |
| 2026-09-20 | **عكس دلالة الإيقاع في الطبقتين:** كانت `speechRate`/`speed` تُمرَّر كـ`1/rateFactor` (=1.087) بينما `rateFactor` في [0.8, 1.0] معرّف في `JarvisVoiceSpec` بأنه "أهدأ من الطبيعي" (والحد الأعلى المسموح 1.0 = الطبيعي) — فكانت الشخصية **أسرع** من الطبيعي مخالفةً §3.1، و`lengthScale = 1.08` في الإعداد كان يتضارب معه (شيربا يشتق `length_scale = 1/speed` ويتجاهل الإعداد كلما مُرّرت `speed ≠ 1`) | تمرير `rateFactor` كما هي في `SherpaOnnxTtsProvider` و`AndroidSystemTtsProvider` (المعاملان في المحركين بنفس الدلالة: مضاعف، 1.0 = الطبيعي)، وحذف `lengthScale` من الإعداد ليبقى **مصدر واحد** للإيقاع | تحديث توقّع السرعة في `JarvisVoiceSessionTest` + CI القادم |
| 2026-09-19 | تحقق محلي لـPhase 7 (البنية الصوتية): 5 إخفاقات من 36 — ثلاثة توقعات لغوية خاطئة في الاختبار (أربعمئة/ألفاً — الكود أصح فصيحاً) + واحد ترتيب خطوات تطبيع الترقيم (التكرار قبل المسافات وإلا تتباعد النقاط) + واحد عقد متشدد زائد (تخطي CLOUD مشروع — تنفيذها اختياري؛ العقد الحقيقي: السلسلة تبدأ بـNEURAL دائماً) | تصحيح التوقعات الثلاثة (الكود هو الصحيح لغوياً) + إعادة ترتيب خطوات الترقيم في الكود + فرض "تبدأ بـNEURAL دائماً" بـrequire في TtsFallbackChain نفسه بدل تخفيف الاختبار | إعادة التشغيل المحلي: **109/109 أخضر** + CI القادم |
| 2026-09-19 | تحقق محلي لـPhase 12: 3 إخفاقات من 17 — اثنان ببيانات اختبار (أدوات لم تُفعَّل عبر البوابة قبل eligibleFor) وواحد بتدفق آلة الحالة (بوابة ثانية من EXECUTING تحتاج إكمال الدورة RESET→UNDERSTAND→PLAN أولاً) + **ثغرة عقد حقيقية في ToolRegistry**: `completePendingEnable(confirmed=true)` كان يفعّل الأداة حتى بعد رفض المستخدم في البوابة (مسار جانبي يخالف "لا مسار جانبي") | إصلاح الكود: الإتمام الآن يتطلب تعلقاً من نفس البوابة (نفس محرك المخاطرة) + دليل تأكيد فعلي في سجل تدقيقها (آخر سجل passedGate لنفس الخطوة)؛ الرفض يمسح التعلق نهائياً. وتصحيح بيانات الاختبار الثلاثة | إعادة التشغيل المحلي: **73/73 أخضر** + CI القادم |
| 2026-09-19 | CI #11 فشل باختبار واحد من أصل 56: اختبار "غير المصنف = CRITICAL" أضاف نسخة من خطوة بنفس المعرّف فكسر عقد فريدية المعرّفات في TaskPlan (IllegalArgumentException) | إعطاء الخطوة المضافة معرّفاً مختلفاً في الاختبار — العقد نفسه صحيح والخطأ في بناء بيانات الاختبار | CI #12 (المتوقع) |
| 2026-09-19 | CI #10 فشل في الترجمة (`compileDebugUnitTestKotlin`): فرعان في `when` من اختبارات Phase 9 استخدما `org.junit.Assert.fail()` — دالة Java تعيد void/Unit فصار نوع التعبير Any بدل List‹Evidence› | استبدالهما بـ `error()` من مكتبة Kotlin القياسية — تعيد Nothing فيُحتفظ بنوع التعبير | CI #11 (ترجمة ناجحة، وبقي اختبار واحد) |
| 2026-09-19 | CI #6 فشل في اختبارات الوحدة: عقد الحالات الجديد (`AgentStateContractTest` في 073cdd6) يطالب بعمل RESET من كل حالة، بينما جدول الانتقالات لم يسمح به من IDLE — ففشل `كل حالة تقبل على الأقل RESET` | إضافة `AgentEvent.RESET` إلى أحداث IDLE في `JarvisStateMachine` (RESET من IDLE = بلا عملية تُعيد إلى IDLE، متسق مع بقية الجدول) | CI #7 (272559e) ✅ |
| 2026-09-19 | خطأ وثائقي مكتشف بالتدقيق: README ذكر 7 حالات للـ Orb و"11 اختبار وحدة" بينما الكود الفعلي 11 حالة و13 اختبار وقت بناء APK الـ CI — الوصف النصي لا يمنع انحراف الوثائق عن الكود | تصحيح README/ARCHITECTURE + إضافة عقد اختباري قابل للتنفيذ `AgentStateContractTest` (عدد/أسماء/مسارات الحالات) يجعل أي انحراف مستقبلي يُفشل البناء | CI #7 |
| 2026-09-19 | عدّاد اختبارات غير صحيح في هذه البوابة: "12 اختبار" بينما الكود المصدري وقت CI #4 يحوي 13 اختبار `@Test` | الاعتماد على عدّاد CI الفعلي كمرجع وحيد وتصحيح الجدول أعلاه | CI #4 (بأثر رجعي عبر `git show ae2a771`) |
| 2026-09-19 | تقرير خارجي: APK يطلب صلاحية `DUMP` signature-level | تتبّع المصدر: المانيفست المحلي وتاريخه وكل تبعيات التشغيل (AARs من Google Maven) سليمة بلا DUMP — المصدر الأرجح فحص خارجي/نسخة أخرى. الإجراء: بوابة قائمة سماح صلاحيات في CI تمنع أي اندماج صلاحية غير مصرح به مستقبلاً | عند أول CI بعد 2026-09-19 |
| Phase 1 | زر "إغلاق" في لوحة المهام كان نصاً غير قابل للنقر (انتهاك: زر لا يعمل) | استُبدل بـ `Modifier.clickable(onClick = onClose)` فعلي | ضمن CI الأول |
| Phase 1 | `size.center` في Orb.kt: خاصية إضافية بدون import صريح — خطر خطأ compilation | حساب المركز يدوياً `Offset(size.width / 2f, size.height / 2f)` | ضمن CI الأول |
| CI #3 | فشل `compileDebugKotlin`: `MainActivity` أشار إلى `sm.lastRejected` كـ StateFlow بينما آلة الحالة تملك متغيراً عادياً فقط — تضارب بين ملفين | جعل آخر رفض حالة تفاعلية حقيقية: `_lastRejected: MutableStateFlow` + `lastRejected: StateFlow` مع إبقاء `lastRejection` كقيمة فورية للاختبارات | CI #4 |
| CI #2 | فشل `processDebugResources`: `mipmap/ic_launcher not found` — المانيفست يشير للأيقونة ولم تُنشأ | إنشاء Adaptive Icon XML كامل (foreground vector بتصميم Orb + خلفية داكنة) — يكفي minSdk 26 بلا PNG | CI #3 |
| CI #1 | فشل step "Grant execute permission to gradlew": ملف `gradlew` لم يكن موجوداً في المستودع (أنشئت wrapper.properties فقط) | إضافة `gradlew` + `gradlew.bat` + `gradle-wrapper.jar` من المصدر الرسمي (وسم v8.10.2 في مستودع Gradle، مطابق لإصدار التوزيعة) والتحقق من سلامة الـ jar | CI #2 |
