# SESSION_START.md — نقطة استئناف سريعة

> اقرأ هذا الملف وحده عند الاستئناف. لا تقرأ SPEC/ARCHITECTURE/README ولا تمسح الكود بحثاً واسعاً.
> آخر تحديث: **2026-09-20** — HEAD قبل هذه الدفعة: `46d2bf8`

## المهمة الحالية
Phase 7 (الصوت): `SherpaOnnxTtsProvider` (NEURAL أساسي — Piper `ar_JO-kareem` int8) ←
`TtsFallbackChain` (NEURAL أولاً، SYSTEM آخراً) ← `JarvisVoiceSession` (نقطة النطق الوحيدة)
← `JarvisApp` / `MainActivity` / `JarvisScreen` (لوحة التشخيص تعرض الطبقة الفعلية).

## ما أُنجز في هذه الدفعة (تحقق بالقراءة: مصدر sherpa-onnx v1.13.8 + `javap` على AAR المحلي)
1. **`EspeakDataInstaller.kt` (جديد)** — نسخ `espeak-ng-data` من assets إلى
   `filesDir/jarvis-tts/espeak-ng-data` (idempotent بعلامة + `phontab`).
   السبب القاطع: `piper-phonemize-lexicon.cc::InitEspeak()` يمرّر data_dir إلى
   `espeak-Initialize` (قراءة `fopen` من نظام ملفات حقيقي)، وإن لم يكن مساراً مطلقاً صالحاً
   → `SHERPA_ONNX_EXIT(-1)` أي **إنهاء عملية التطبيق** لا استثناء.
2. **`SherpaOnnxTtsProvider`** — `dataDir` = مسار مطلق للمجلد المنسوخ + تحقق مسبق من الأصول
   (يحوّل الأصل المفقود إلى fallback نظيف) + إزالة `lengthScale` المتضارب مع `speed`.
3. **`TtsFallbackChain`** — تهيئة متأخرة تلقائية لكل طبقة (`prepare`) مع عزل الفشل.
   كان `prepare()` **لا يُستدعى في الإنتاج إطلاقاً** → NEURAL دائماً NotSupported وSYSTEM دائماً
   يرمي → **لا صوت بتاتاً**.
4. **الشخصية** — تصحيح السرعة: تُمرَّر `rateFactor` كما هي (لا مقلوبها) في الطبقتين؛
   القلب كان يجعل الإيقاع أسرع من الطبيعي (مخالفاً §3.1).
5. **اختبارات** — `EspeakDataInstallerTest` (جديد) + اختبارا تهيئة في `TtsFallbackChainTest`
   + تحديث توقّع السرعة في `JarvisVoiceSessionTest`.

## التالي (غير مكتمل — الترتيب إلزامي)
- تشغيل على جهاز Android حقيقي (يبقى CI هو فحص البناء): سماع التحية العربية فعلياً + تقييم الجودة
  ضد §3.1 + إثبات الـfallback عند فشل Sherpa. قائمة Phase 1 في FEATURE_STATUS.md.
- Phase 2 (تعميق Orb بالصوت) ثم 3 (ميكروفون/صلاحيات) ثم 4 (STT).
- لا تبدأ فيز 12-19 قبل APPROVED للفيز 10/11 (كلاهما APPROVED فعلاً).

## قواعد العمل الثابتة
- اكتب بملفات مجمّعة، اختبارات قليلة ذات قيمة عقدية، تحقّق بقراءة دقيقة (لا Android SDK محلي).
- CI هو فحص البناء: `commit + push` في نهاية كل دفعة.
- بعد كل دفعة: حدّث هذا الملف + جدول Phase في FEATURE_STATUS.md + سجل الفشل.
- مفتاح البيئة: `app/libs/sherpa-onnx-1.13.8.aar` (50MB) و`app/src/main/assets/tts/**` (37MB)
  ملفات موديل/AAR **مضمّنة في المستودع** لأن CI لا يجلبها من الشبكة.
