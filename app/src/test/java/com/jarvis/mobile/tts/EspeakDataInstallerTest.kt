package com.jarvis.mobile.tts

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات عقد مثبّت بيانات espeak-ng (Phase 7 — ربط TTS العصبي):
 * - نسخ **تكراري** لكامل الشجرة (espeak-ng-data تحتوي lang/ وvoices/ متداخلة).
 * - Idempotent: لا إعادة نسخ بعد اكتمالها (تحقق بعلامة + phontab).
 * - نسخة ناقصة/محذوفة تُعاد كاملة (العلامة تُكتب أخيراً فقط).
 * - مجلد البيانات النهائي يبقى مطابقاً للأصل (العلامة خارجه).
 *
 * تحدث بلا Android SDK: الشجرة مجرّدة عبر [EspeakDataInstaller.AssetTree].
 */
class EspeakDataInstallerTest {

    private val root = Files.createTempDirectory("jarvis-espeak").toFile()

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    /** شجرة أصول وهمية في الذاكرة: مجلدات → أسماء أبنائها، وملفات → محتواها. */
    private class FakeAssetTree(
        private val dirs: Map<String, List<String>>,
        private val files: Map<String, String>,
    ) : EspeakDataInstaller.AssetTree {
        var openCount = 0
            private set

        override fun children(path: String): List<String> = dirs[path] ?: emptyList()

        override fun open(path: String) = files.getValue(path).byteInputStream().also { openCount++ }
    }

    private fun fixture() = FakeAssetTree(
        dirs = mapOf(
            SOURCE to listOf("phontab", "phondata", "ar_dict", "lang", "voices"),
            "$SOURCE/lang" to listOf("sem"),
            "$SOURCE/lang/sem" to listOf("ar"),
            "$SOURCE/voices" to listOf("!v"),
            "$SOURCE/voices/!v" to listOf("f1"),
        ),
        files = mapOf(
            "$SOURCE/phontab" to "phontab-bytes",
            "$SOURCE/phondata" to "phondata-bytes",
            "$SOURCE/ar_dict" to "ar-dict-bytes",
            "$SOURCE/lang/sem/ar" to "arabic-voice-def",
            "$SOURCE/voices/!v/f1" to "variant",
        ),
    )

    private fun installer(source: EspeakDataInstaller.AssetTree) =
        EspeakDataInstaller(targetDir = File(root, "espeak-ng-data"), source = source, sourceRoot = SOURCE)

    @Test
    fun `ينسخ الشجرة كاملة بشكل تكراري - بما فيها المجلدات المتداخلة`() {
        val tree = fixture()

        val target = installer(tree).installIfNeeded()

        assertEquals("phontab-bytes", File(target, "phontab").readText())
        assertEquals("arabic-voice-def", File(target, "lang/sem/ar").readText())
        assertEquals("variant", File(target, "voices/!v/f1").readText())
        assertEquals("كل ملفات الأصل نُسخت", 5, target.walkTopDown().filter { it.isFile }.count())
    }

    @Test
    fun `لا إعادة نسخ بعد اكتمالها - idempotent`() {
        val tree = fixture()
        val subject = installer(tree)

        subject.installIfNeeded()
        val copiesAfterFirst = tree.openCount
        subject.installIfNeeded()

        assertEquals("النسخة الثانية لا تفتح أي أصل", copiesAfterFirst, tree.openCount)
    }

    @Test
    fun `نسخة ناقصة (بلا علامة إتمام) تُعاد كاملة`() {
        val tree = fixture()
        val subject = installer(tree)
        val target = subject.installIfNeeded()

        // محاكاة نسخة منقطعة: العلامة غائبة وملف أساسي محذوف
        File(root, MARKER).delete()
        assertTrue(File(target, "phontab").delete())

        subject.installIfNeeded()

        assertTrue("الملف الأساسي أُعيد", File(target, "phontab").isFile)
        assertEquals("phondata-bytes", File(target, "phondata").readText())
    }

    @Test
    fun `مجلد البيانات النهائي مطابق للأصل - العلامة خارجه`() {
        val target = installer(fixture()).installIfNeeded()

        assertFalse("لا ملفات دخيلة داخل مجلد espeak", target.listFiles()!!.any { it.name.startsWith(".") })
        assertTrue(File(root, MARKER).isFile)
    }

    private companion object {
        const val SOURCE = "tts/vits-piper-ar_JO-kareem-medium-int8/espeak-ng-data"
        const val MARKER = ".espeak-data-copy-v1"
    }
}
