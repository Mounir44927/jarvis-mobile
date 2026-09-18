# Jarvis Mobile

مساعد شخصي صوتي ذكي (Agentic Assistant) للأندرويد، عربي أولاً، يعمل بمعمارية:
فهم ← تخطيط ← أدوات ← تنفيذ ← مراقبة ← تحقق ← إصلاح ← نتيجة.

**نظام البناء:** BUILD → TEST → VERIFY → FIX → RETEST → APPROVE → NEXT.
لا تُعلن ميزة "تمت" إلا بأدلة اختبار فعلية (انظر `FEATURE_STATUS.md`).

## الحالة الحالية

Phase 0 + Phase 1 (هيكل المشروع + الواجهة + آلة الحالة) — التفاصيل في:
- `ARCHITECTURE.md` — المعمارية وقرارات التصميم
- `DEPENDENCIES.md` — كل تبعية وسببها وترخيصها وتحليل مجانيتها
- `FEATURE_STATUS.md` — بوابة الجودة لكل ميزة

## ما هو منفذ فعلياً الآن
- مشروع Android كامل قابل للبناء (Gradle 8.10.2 + AGP 8.7.3 + Kotlin 2.0.21 + Compose).
- آلة حالة مركزية بجدول انتقالات صريح يرفض أي انتقال غير منطقي.
- واجهة داكنة مستقبلية مع Orb متحرك يستجيب لكل الحالات: Idle/Listening/Thinking/Speaking/Executing/Error/Success.
- لوحة حالة المهمة تعرض بيانات آلة الحالة الحقيقية فقط.
- 11 اختبار وحدة لآلة الحالة وعقد المهمة.
- CI مجاني عبر GitHub Actions يبني APK ويصدره كـ artifact مع كل push.

## ما هو NOT IMPLEMENTED بعد (بترتيب Phases)
الميكروفون (Phase 3)، STT (4)، LLM/Gemini (5)، رد محادثة عربي (6)، TTS (7)،
حلقة الصوت الكاملة (8)، Task Planner (9)، Tool Registry (10)، Android Intents (11)،
Accessibility (12)، الإشعارات (13)، الملفات (14)، البحث في الويب (15)، Browser Agent (16)،
Sandbox للكود (17)، الذاكرة (18)، Verification Engine (19)، Self-Healing (20)،
محرك المخاطرة والتأكيد (21)، الأداء (22)، اختبار جهاز حقيقي شامل (23)، Regression (24)، Release (25).

## بناء APK عبر CI (المسار المعتمد حالياً)
1. اربط المستودع بـ GitHub (المشروع محلي الآن).
2. أي push يشغّل `Jarvis CI`: اختبارات وحدة + بناء APK.
3. حمّل الـ APK من صفحة الـ workflow → Artifacts → `jarvis-debug-apk`.
4. ثبّته على الهاتف (تفعيل "تثبيت من مصادر غير معروفة").

> **اختبار جهاز حقيقي (القسم 14):** بعد تثبيت الـ APK، اختبر: دوران الشاشة، إغلاق التطبيق وإعادة فتحه،
> الرجوع للخلفية والعودة، وضع البطارية المنخفض. سجّل النتائج في `FEATURE_STATUS.md`.

## البناء محلياً
```bash
./gradlew testDebugUnitTest   # اختبارات الوحدة
./gradlew assembleDebug       # APK تجريبي
```

## الأسرار (القسم 44)
لا مفتاح API داخل الكود أو Git — أبداً. مفتاح Gemini سيُدخل من إعدادات التطبيق
(EncryptedSharedPreferences) في Phase 5، وتُمنع تسريته من السجلات.
