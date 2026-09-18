# Jarvis Mobile — Dependencies

القاعدة (القسم 16/43): **ZERO PAID DEPENDENCY** — لا تُضاف أي تبعية قبل التحقق من:
open-source؟ الترخيص؟ API مدفوع؟ حدود الاستخدام؟ حساب مطلوب؟ قيود تجارية؟

## التبعيات الحالية (Phase 0-1)

| التبعية | الإصدار | الترخيص | مجانية؟ | السبب |
|---------|---------|---------|---------|-------|
| Android Gradle Plugin | 8.7.3 | Apache-2.0 (جزء من Android SDK، مجاني بالكامل) | ✅ | أدوات البناء الرسمية |
| Gradle | 8.10.2 | Apache-2.0 | ✅ | نظام البناء |
| Kotlin (stdlib + android plugin) | 2.0.21 | Apache-2.0 | ✅ | لغة التطوير |
| Jetpack Compose (BOM 2024.12.01) | BOM | Apache-2.0 | ✅ | واجهة Orb المتحركة |
| activity-compose | 1.9.3 | Apache-2.0 | ✅ | ربط Activity بـ Compose |
| androidx.core-ktx | 1.15.0 | Apache-2.0 | ✅ | utilities رسمية |
| androidx.lifecycle-runtime-ktx | 2.8.7 | Apache-2.0 | ✅ | دورة حياة + StateFlow |
| kotlinx-coroutines | 1.9.0 | Apache-2.0 | ✅ | تنفيذ غير متزامن (صوت/مهام) |
| JUnit 4 | 4.13.2 | EPL-1.0 | ✅ | اختبارات الوحدة |
| GitHub Actions (ubuntu runner) | — | مجاني للمستودعات العامة ضمن الحصة الممنوحة | ✅ (حصة مجانية، بلا بطاقة) | بناء APK — انظر README |

**كل ما سبق:** بلا حسابات مدفوعة، بلا بطاقات، بلا حدود استخدام تقاطعية. المستودعات المستخدمة: Google Maven + Maven Central.

## التبعيات المخططة (تُدخل في Phase الخاص بها بعد التحقق نفسه)

| المكوّن | المرشّح | الترخيص | الميزانية المجانية | القرار |
|---------|---------|---------|--------------------|--------|
| LLM (أساسي) | **Gemini API** (gemini-flash/free tier) | خدمة Google بلا تكلفة ضمن الحصة | ~10 RPM و~250K TPM و~1500 RPD على الموديلات Flash المجانية (يُراجع وقت الإدخال — الحصص تتغير) | يدخل عبر `LLMProvider` abstraction + Fallback (القسم 17-18). المفتاح من إعدادات التطبيق المشفرة فقط |
| STT أساسي | **Android SpeechRecognizer / RecognizerIntent** | نظام Android | ✅ مجاني على الجهاز/خدمة Google النظام | أولاً: جودة عربية أفضل وأقل استهلاكاً |
| STT offline | **Vosk** (alphacep/vosk-api) | Apache-2.0 | ✅ مكتبة مجانية + نموذج vosk-model-small-ar (~50MB، يتطلب ~300MB RAM وقت التشغيل) | مزوّد STT بديل بدون إنترنت — يُقيَّم على أجهزة متوسطة قبل الاعتماد |
| STT مرجع | whisper.cpp | MIT | ✅ | مرجع جودة للعربية، ويُقيَّم لاحقاً كـ Provider |
| Agent framework | **LangChain4j** | Apache-2.0 | ✅ مكتبة | يُقيَّم من Phase 9+ للأدوات/الذاكرة بدل إعادة اختراعها (BOM 1.x حالياً) |
| Web research مرجع | **Browser Use** | MIT | ✅ مرجع منهجي | مرجع تصميم لوحدة الويب؛ التنفيذ فعلياً عبر HTTP APIs أولاً ثم أتمتة متصفح عند الحاجة (القسم 4) |
| PDF | Android's PdfRenderer (قراءة) + مكتبة إنشاء مفتوحة المصدر | Apache-2.0 | ✅ | Phase 14 |
| TTS | System TTS engine (يوجد على الأجهزة) + ArabicTextNormalizer خاص | — | ✅ | Phase 7؛ محركات أعلى جودة تُقيَّم كـ Provider لاحق |

## ممنوعات
- أي مفتاح API في الكود أو Git أو Logs (القسم 44).
- أي خدمة تتطلب بطاقة لتفعيل "المجاني".
- أي تبعية بترخيص GPL تُدمج داخل APK قبل مراجعة توافقها (المكتبات المذكورة أعلاه كلها Apache-2.0/MIT/EPL-ساحة اختبار فقط).

## بروتوكول إضافة تبعية جديدة
1. تحقق من الترخيص والنشاط والتوافق مع Android وحجم الأثر.
2. أضف سطراً في هذا الملف مع سبب التحقق.
3. أضف اختبار regression يغطي الميزة التي تخدمها.
