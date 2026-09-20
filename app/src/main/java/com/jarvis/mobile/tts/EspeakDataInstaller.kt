package com.jarvis.mobile.tts

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.InputStream

/**
 * مُثبِّت بيانات espeak-ng — ينسخ شجرة `espeak-ng-data` من assets إلى تخزين التطبيق الحقيقي.
 *
 * لماذا هذا **إلزامي** (وتحقق بالقراءة من مصدر sherpa-onnx v1.13.8)؟
 * espeak-ng (المدمج في sherpa-onnx) يقرأ `phontab/phondata/phonindex/intonations/lang/**_dict`
 * من نظام ملفات حقيقي — لا يفهم مسارات assets إطلاقاً. في
 * `piper-phonemize-lexicon.cc::InitEspeak()` يمرَّر data_dir إلى `espeak_Initialize`،
 * وإن لم يكن مساراً مطلقاً صالحاً يسجّل:
 *   "You need to follow our examples to copy the espeak-ng-data directory from the
 *    assets folder to an external storage directory."
 * ثم `SHERPA_ONNX_EXIT(-1)` — أي **إنهاء عملية التطبيق كاملة** لا استثناء يمكن التقاطه.
 * لذا: ننسخ الشجرة (355 ملفاً، ~18MB) مرة واحدة، ثم نمرّر مساراً مطلقاً في `dataDir`.
 *
 * النسخ يتم عبر `AssetManager.open()` (طرف Java) — يعمل سواء ضُغط الأصل في الـAPK أم لا،
 * بخلاف القراءة الأصلية (AASSET_MODE_BUFFER) التي تتطلب أصولاً غير مضغوطة (الموديل/tokens).
 *
 * Idempotent: وجود علامة إتمام + ملف `phontab` يمنعان إعادة النسخ في كل تشغيل.
 */
class EspeakDataInstaller(
    private val targetDir: File,
    private val source: AssetTree,
    private val sourceRoot: String,
) {
    /** شجرة أصول مجرّدة — تجعل منطق النسخ التكراري قابلاً للاختبار بلا Android SDK. */
    interface AssetTree {
        /** أسماء الأبناء المباشرين (ملفات/مجلدات) لمسار أصل — قائمة فارغة تعني "ملف". */
        fun children(path: String): List<String>

        fun open(path: String): InputStream
    }

    /** ينسخ الشجرة إن لم تكن مثبتة مكتملة، ثم يعيد المجلد النهائي (المسار المطلق لـespeak-ng). */
    fun installIfNeeded(): File {
        // إكمال النسخة السابقة = العلامة + ملف أساسي فعلي موجود (حماية من نسخة ناقصة/محذوفة)
        if (markerFile.isFile && File(targetDir, REQUIRED_FILE).isFile) return targetDir

        if (targetDir.exists() && !targetDir.deleteRecursively()) {
            error("تعذّر تنظيف نسخة espeak-ng-data السابقة: $targetDir")
        }
        check(targetDir.mkdirs() || targetDir.isDirectory) {
            "تعذّر إنشاء مجلد بيانات espeak-ng: $targetDir"
        }

        copyTree(sourceRoot, targetDir)

        // العلامة تُكتب أخيراً: نسخة منقطعة (انهيار/إغلاق) لا تُعدّ مكتملة
        markerFile.writeText(COPY_VERSION)
        return targetDir
    }

    private fun copyTree(assetPath: String, outDir: File) {
        for (name in source.children(assetPath)) {
            val childAsset = "$assetPath/$name"
            val childOut = File(outDir, name)
            val grandchildren = source.children(childAsset)
            if (grandchildren.isEmpty()) {
                source.open(childAsset).use { input ->
                    childOut.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                check(childOut.mkdirs() || childOut.isDirectory) {
                    "تعذّر إنشاء مجلد بيانات فرعي: $childOut"
                }
                copyTree(childAsset, childOut)
            }
        }
    }

    /** العلامة **خارج** مجلد البيانات — يبقى مجلد espeak مطابقاً للأصل بلا ملفات دخيلة. */
    private val markerFile: File
        get() = File(targetDir.parentFile, MARKER_NAME)

    companion object {
        /** اسم مجلد بيانات espeak-ng كما في حزمة النموذج (piper). */
        const val ESPEAK_ASSET_DIR = "espeak-ng-data"

        private const val MARKER_NAME = ".espeak-data-copy-v1"
        private const val COPY_VERSION = "v1"

        /** ملف إلزامي من شجرة espeak-ng-data — يُستخدم كتحقق من اكتمال النسخة. */
        private const val REQUIRED_FILE = "phontab"

        /** مُثبِّت جاهز لتطبيق Android: assets ← filesDir/jarvis-tts/espeak-ng-data. */
        fun forAppAssets(appContext: Context, sourceRoot: String): EspeakDataInstaller =
            EspeakDataInstaller(
                targetDir = File(File(appContext.filesDir, "jarvis-tts"), ESPEAK_ASSET_DIR),
                source = AndroidAssetTree(appContext.assets),
                sourceRoot = sourceRoot,
            )

        private class AndroidAssetTree(private val assets: AssetManager) : AssetTree {
            override fun children(path: String): List<String> =
                assets.list(path)?.toList() ?: emptyList()

            override fun open(path: String): InputStream = assets.open(path)
        }
    }
}
