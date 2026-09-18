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
| **Status** | **APPROVED** | — |

---

## Phase 1 — Android Shell + UI الأساسية

| البند | الحالة | الدليل |
|-------|--------|--------|
| Build | ⏳ PENDING | CI: أول `assembleDebug` ناجح على GitHub Actions |
| Unit Tests (آلة الحالة + عقد المهمة — 12 اختبار) | ⏳ PENDING | `testDebugUnitTest` في CI |
| Integration | — NOT STARTED — | لا يوجد شيء للتكامل بعد |
| Real Device | ⏳ PENDING | تثبيت APK artifact من CI واختبار يدوي (القائمة أدناه) |
| Error Handling | ✅ PASS | آلة الحالة ترفض الانتقالات غير المنطقية + UI يعرض آخر رفض |
| Arabic | ✅ PASS | واجهة عربية أصلية RTL، علامات الحالة بالعربية |
| Performance | ⏳ PENDING | قياس على جهاز حقيقي |
| **Status** | **NOT APPROVED YET** | ينتظر: CI أخضر + اختبار جهاز حقيقي |

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

## Phases 2-25 — NOT STARTED

Phase 2 (تعميق Orb بالصوت الفعلي)، 3 (الميكروفون+الصلاحيات)، 4 (STT)، 5 (Gemini Provider)،
6 (الرد العربي)، 7 (TTS+ArabicTextNormalizer)، 8 (حلقة الصوت الكاملة)، 9 (Task Planner)،
10 (Tool Registry)، 11 (Intents)، 12 (Accessibility)، 13 (الإشعارات)، 14 (الملفات)،
15 (Web Search)، 16 (Browser Agent)، 17 (Code Sandbox)، 18 (Memory)، 19 (Verification)،
20 (Self-Healing)، 21 (Risk/Confirmation)، 22 (الأداء)، 23 (جهاز حقيقي شامل)،
24 (Regression)، 25 (Release).

كل Phase لن تظهر هنا إلا بجدول اختبار كامل وحالة فعلية.

---

## سجل الفشل والإصلاح (مطلوب — القسم 12)

| التاريخ | ما فشل | كيف أُصلح | إعادة الاختبار |
|---------|--------|-----------|----------------|
| Phase 1 | زر "إغلاق" في لوحة المهام كان نصاً غير قابل للنقر (انتهاك: زر لا يعمل) | استُبدل بـ `Modifier.clickable(onClick = onClose)` فعلي | ضمن CI الأول |
| Phase 1 | `size.center` في Orb.kt: خاصية إضافية بدون import صريح — خطر خطأ compilation | حساب المركز يدوياً `Offset(size.width / 2f, size.height / 2f)` | ضمن CI الأول |
| CI #3 | فشل `compileDebugKotlin`: `MainActivity` أشار إلى `sm.lastRejected` كـ StateFlow بينما آلة الحالة تملك متغيراً عادياً فقط — تضارب بين ملفين | جعل آخر رفض حالة تفاعلية حقيقية: `_lastRejected: MutableStateFlow` + `lastRejected: StateFlow` مع إبقاء `lastRejection` كقيمة فورية للاختبارات | CI #4 |
| CI #2 | فشل `processDebugResources`: `mipmap/ic_launcher not found` — المانيفست يشير للأيقونة ولم تُنشأ | إنشاء Adaptive Icon XML كامل (foreground vector بتصميم Orb + خلفية داكنة) — يكفي minSdk 26 بلا PNG | CI #3 |
| CI #1 | فشل step "Grant execute permission to gradlew": ملف `gradlew` لم يكن موجوداً في المستودع (أنشئت wrapper.properties فقط) | إضافة `gradlew` + `gradlew.bat` + `gradle-wrapper.jar` من المصدر الرسمي (وسم v8.10.2 في مستودع Gradle، مطابق لإصدار التوزيعة) والتحقق من سلامة الـ jar | CI #2 |
