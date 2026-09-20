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

## الصلاحيات المعلنة وقائمة السماح (2026-09-19)

**قاعدة:** أمن الصلاحيات جزء من بوابة الجودة. ملف `app/src/main/AndroidManifest.xml` هو **المصدر الوحيد** للصلاحيات المعلنة — لا تُضاف صلاحية إلا هنا ومع بند توثيقي أدناه، وبوابة CI (خطوة "Permissions allowlist gate") تُفشل أي بناء يعلن صلاحية خارج هذه القائمة (تلتقط أي اندماج صامت من تبعية مستقبلية).

| الصلاحية | المستوى | السبب | Phase |
|----------|---------|-------|-------|
| INTERNET | normal | مطلوبة من Phase 5 (Gemini REST) والبحث في الويب (17) — معلنة مبكراً لثبات المانيفست | 5+ |
| RECORD_AUDIO | dangerous | المدخل الصوتي (Phase 3) — تُطلب وقت الحاجة runtime، لا عند التثبيت | 3 |
| POST_NOTIFICATIONS | runtime | إشعارات Jarvis (Phase 15) | 15 |
| QUERY_ALL_PACKAGES | normal | التعامل مع التطبيقات المسطّبة (Phase 13) — Play يطلب إعلان استخدام؛ متجر F-Droid/تثبيت مباشر بلا قيود | 13 |
| VIBRATE | normal | تأكيد لمسي عند طلب تأكيد العمليات الحساسة (Phase 10) | 10 |
| WAKE_LOCK | normal | إبقاء جلسة الصوت نشطة أثناء الاستماع الطويل (Phase 8) | 8 |
| FOREGROUND_SERVICE | normal | خدمة الجلسة الصوتية الخلفية (Phase 8+) | 8+ |
| RECEIVE_BOOT_COMPLETED | normal | إعادة تشغيل خدمة الصوت بعد الإقلاع — تُقيَّم وقت الحاجة وقد تُحذف | 8+ |
| USE_FULL_SCREEN_INTENT | special-app-access | تنبيه ملء الشاشة عند طلب تأكيد عملية خطرة (Phase 10) | 10 |
| CAMERA | dangerous | التقاط صور كمدخل للمهام (Phase 13+) — تُقيَّم وقت الحاجة وقد تُحذف | 13+ |
| READ_MEDIA_IMAGES / READ_MEDIA_VIDEO / READ_MEDIA_AUDIO / READ_MEDIA_VISUAL_USER_SELECTED | dangerous | قراءة ملفات المستخدم (Phase 16) — تُطلب runtime وقت الحاجة فقط | 16 |
| WRITE_EXTERNAL_STORAGE / READ_EXTERNAL_STORAGE | dangerous | أرشيف Android ≤12 (maxSdkVersion=32) — الاعتماد الأساسي على SAF/MediaStore بلا صلاحيات | 16 |
| MANAGE_EXTERNAL_STORAGE | special | *غير مفعّلة* — تُفعَّل فقط إذا ثبتت حاجة فعلياً؛ الأفضلية دائماً لـ SAF/MediaStore/ملفات التطبيق الخاصة | 16 (احتياط) |
| ACCESS_NETWORK_STATE | normal | فحص الاتصال قبل STT/LLM السحابي | 4 |
| BLUETOOTH_CONNECT | dangerous | سماعة بلوتوث كمدخل صوتي (Phase 8) — تُقيَّم وقت الحاجة | 8+ |
| SYSTEM_ALERT_WINDOW | special | *غير مفعّلة افتراضياً* — ربما لاحقاً لطبقة تأكيد عائمة؛ تتطلب نية المستخدم الصريحة | لاحقاً (غير مفعّلة) |
| PACKAGE_USAGE_STATS | signature/AppOps | *غير مفعّلة افتراضياً* — كشف التطبيق النشط؛ يتطلب موافقة المستخدم من الإعدادات | لاحقاً (غير مفعّلة) |
| BIND_ACCESSIBILITY_SERVICE | signature | تُعلن فقط كـ `<permission>` على خدمة الوصول نفسها (Phase 14) — لا تُطلب كـ uses-permission | 14 |
| NFC | normal | غير مفعّلة — تُقيَّم فقط إذا احتاجتها ميزة فعلية | لاحقاً (غير مفعّلة) |

**ممنوعة دائماً (signature/system-level — لا يمكن منحها لتطبيق عادي ولا لها use case عندنا):**
DUMP، PACKAGE_USAGE_STATS كطلب مباشر، WRITE_SECURE_SETTINGS، READ_LOGS، WRITE_APN_SETTINGS،
CALL_PHONE، SEND_SMS، READ_SMS، RECEIVE_SMS، READ_CALL_LOG، WRITE_CALL_LOG، PROCESS_OUTGOING_CALLS،
RECORD_AUDIO في خدمة خلفية بلا إشعار مستخدم، أي صلاحية تظهر من manifest merge بلا بند توثيقي هنا.

### سجل تتبع تقرير DUMP (2026-09-19)

**التقرير:** فحص جزئي لـ `jarvis-debug-apk` من آخر بناء CI أظهر طلب `android.permission.DUMP`
بجانب INTERNET وRECORD_AUDIO.

**التحقيق (بالأدلة):**
1. `app/src/main/AndroidManifest.xml` يعلن INTERNET وRECORD_AUDIO فقط (سطرا 4 و7) — لا DUMP بأي شكل (لا uses-permission ولا permission ولا أدوات `tools:`).
2. تاريخ المانيفست: commit واحد (dcf1475، الإنشاء) بلا أي تعديل بعده — لم تكن DUMP موجودة قط في هذا المستودع.
3. تبعيات التشغيل الحالية (مطابقة libs.versions.toml وBOM 2024.12.01): فُحص AAR كلٍّ منها فعلياً من Google Maven (core-ktx 1.15.0، activity 1.9.3، activity-compose 1.9.3، lifecycle-runtime-ktx 2.8.7، lifecycle-runtime 2.8.7، compose ui/ui-graphics/ui-tooling/runtime/foundation 1.7.6، material3 1.3.1): **ولا واحدة تعلن DUMP**. الوحيدة التي تضيف صلاحية: core-ktx تضيف `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature-level، يعرّفه التطبيق نفسه، غير حساسة، نمط رسمي من AndroidX).
4. **الخلاصة:** لا يوجد DUMP في أي مصدر داخل هذا المستودع. الاحتمالات الأرجح: فحص أُجري على APK من مصدر/نسخة أخرى (مثلاً تطبيق آخر على الجهاز أو نسخة قديمة)، أو أداة فحص خرجت بتقرير خاطئ، أو قراءة dex/META-INF بدل صلاحيات المانيفست المدمج.

**الإجراء (وقائي دائم):**
- بوابة CI: خطوة "Permissions allowlist gate" في `.github/workflows/build.yml` — تقارن الصلاحيات المعلنة بقائمة السماح أعلاه وتُفشل البناء على أي زيادة.
- عند أي APK مستقبلي يُفحص يدوياً: استخدم `aapt dump permissions jarvis-debug-apk` أو
  `apkanalyzer manifest print` على المانيفست **المدمج** — لا dex (dex لا يعكس الصلاحيات).
- أي تبعية جديدة تُفحص مانيفستها قبل القبول (خطوة 1 من بروتوكول الإضافة أدناه).

## التبعيات المخططة (تُدخل في Phase الخاص بها بعد التحقق نفسه)

| المكوّن | المرشّح | الترخيص | الميزانية المجانية | القرار |
|---------|---------|---------|--------------------|--------|
| LLM (أساسي) | **Gemini API** (gemini-flash/free tier) | خدمة Google بلا تكلفة ضمن الحصة | ~10 RPM و~250K TPM و~1500 RPD على الموديلات Flash المجانية (يُراجع وقت الإدخال — الحصص تتغير) | يدخل عبر `LLMProvider` abstraction + Fallback (القسم 17-18). المفتاح من إعدادات التطبيق المشفرة فقط |
| STT أساسي | **Android SpeechRecognizer / RecognizerIntent** | نظام Android | ✅ مجاني على الجهاز/خدمة Google النظام | أولاً: جودة عربية أفضل وأقل استهلاكاً |
| STT offline | **Vosk** (alphacep/vosk-api) | Apache-2.0 | ✅ مكتبة مجانية + نموذج vosk-model-small-ar (~50MB، يتطلب ~300MB RAM وقت التشغيل) | مزوّد STT بديل بدون إنترنت — يُقيَّم على أجهزة متوسطة قبل الاعتماد |
| STT مرجع | whisper.cpp | MIT | ✅ | مرجع جودة للعربية، ويُقيَّم لاحقاً كـ Provider |
| Agent framework | **LangChain4j** | Apache-2.0 | ✅ مكتبة | يُقيَّم من Phase 9+ للأدوات/الذاكرة بدل إعادة اختراعها (BOM 1.x حالياً) |
| Web research مرجع | **Browser Use** | MIT | ✅ مرجع منهجي | مرجع تصميم لوحدة الويب؛ التنفيذ فعلياً عبر HTTP APIs أولاً ثم أتمتة متصفح عند الحاجة (القسم 4) |
| PDF | Android's PdfRenderer (قراءة) + مكتبة إنشاء مفتوحة المصدر | Apache-2.0 | ✅ | Phase 16 (الملفات — الترتيب الجديد SPEC.md) |
| TTS Neural (أساسي) | **sherpa-onnx** (k2-fsa/sherpa-onnx) | Apache-2.0 | ✅ مكتبة مجانية + نماذج مجانية (بما فيها أصوات عربية رجولية مثل Piper ar_JO) — offline على aarch64 | Phase 7 — الأساس حسب ADR-7/11 المنقح: المواصفة الصوتية (فخم/عميق/طبيعي) لا يحققها System TTS الافتراضي. يُفحص الـ AAR وقت الإدخال (بروتوكول الإضافة أعلاه) |
| TTS fallback | System TTS engine (يوجد على الأجهزة) | نظام Android | ✅ | Phase 7 — **Fallback فقط** لا الخيار الرئيسي (ADR-7 المنقح 2026-09-19) + ArabicTextNormalizer خاص بيننا |

## sherpa-onnx v1.13.8 — فحص الإدخال الفعلي (2026-09-19)

- **AAR:** `app/libs/sherpa-onnx-1.13.8.aar` من الإصدار الرسمي v1.13.8 على GitHub Releases (k2-fsa/sherpa-onnx).
- **SHA-256:** `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96` (مُتحقَّق وقت الإدخال، أُعيد التحقق محلياً 2026-09-20 وهو مطابق — **بوابة CI تفرضه الآن** عند كل بناء).
- **فحص الـ AAR فعلياً:** AndroidManifest الداخلي بلا أي uses-permission (لا اندماج صامت للصلاحيات)؛ minSdk 21؛ حزم JNI للمعمارات الأربع (arm64-v8a، armeabi-v7a، x86، x86_64)؛ كلاسات `com.k2fsa.sherpa.onnx.OfflineTts*` (Kotlin metadata mv=1.7 متوافقة مع Kotlin 2.0.21).
- **النموذج:** `vits-piper-ar_JO-kareem-medium-int8` (عربي رجولي، متحدث واحد، 22.05kHz) من release tag `tts-models`. SHA-256 **للملف المنزَّل وقت الإدخال** (قبل الاستخراج): `215910431bbe8236b77242b19d869a4eda6073e9cd424bec45b6d4a59a790d82`.
- **الملفات المضمّنة فعلياً في المستودع** تحت `app/src/main/assets/tts/vits-piper-ar_JO-kareem-medium-int8/` — هذه هي الأصول التي يقرأها التطبيق، ولها SHA-256 مُتحقَّق محلياً بـ`sha256sum` (2026-09-20) وتفرضه بوابة CI:
  - `ar_JO-kareem-medium.onnx` (18,579,711 بايت، int8): `71ff7b08354a3c9a15859fdb9533499bc51df6b431867773a864f8c9c7c3c9a1`
  - `tokens.txt` (159 سطراً، eSpeak IPA): `620e1aecf1a68fea3ba5850d137b0138fa2037c9b372dad13b95a2a215d0849a`
  - `espeak-ng-data/` (355 ملفاً، ~18MB، مع `MODEL_CARD`): بيانات espeak-ng الكاملة؛ **يجب نسخها إلى نظام ملفات حقيقي** قبل إنشاء المحرك (espeak-ng لا يقرأ من assets — التفاصيل في `tts/EspeakDataInstaller.kt`).
- قراءة الأرقام/التطبيع عربي داخلي عندنا (ArabicTextNormalizer) — لا تبعية إضافية.
- **الترخيص:** sherpa-onnx Apache-2.0؛ نموذج kareem مجاني (راجع MODEL_CARD/المصدر داخل الأصول). بلا حسابات/بطاقات/مفاتيح.

| الصلاحية من الـ AAR | الحالة |
|---------------------|--------|
| (لا شيء — الـAAR بلا صلاحيات) | ✅ لا تأثير على قائمة السماح |

## ممنوعات
- أي مفتاح API في الكود أو Git أو Logs (القسم 44).
- أي خدمة تتطلب بطاقة لتفعيل "المجاني".
- أي تبعية بترخيص GPL تُدمج داخل APK قبل مراجعة توافقها (المكتبات المذكورة أعلاه كلها Apache-2.0/MIT/EPL-ساحة اختبار فقط).

## بروتوكول إضافة تبعية جديدة
1. تحقق من الترخيص والنشاط والتوافق مع Android وحجم الأثر.
2. افحص `AndroidManifest.xml` داخل الـ AAR: أي uses-permission تُدخلها يجب أن تكون ضمن قائمة السماح أعلاه — وإلا تُستبعد التبعية أو تُعالج (`tools:node="remove"` موثق هنا).
3. أضف سطراً في هذا الملف مع سبب التحقق.
4. أضف اختبار regression يغطي الميزة التي تخدمها.
