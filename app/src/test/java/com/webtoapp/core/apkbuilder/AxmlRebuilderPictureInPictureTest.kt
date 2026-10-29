package com.webtoapp.core.apkbuilder

import com.android.aapt.Resources
import com.google.common.truth.Truth.assertThat
import com.webtoapp.core.playstore.aab.axml.AxmlToProtoXml
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Verifies [AxmlRebuilder.expandAndModifyFull] flips `android:supportsPictureInPicture`
 * on `ShellActivity` when the exported app enables PiP.
 *
 * The shell template always declares the attribute as `false`: Android only honours
 * `enterPictureInPictureMode()` for activities that opt in at manifest level, so the
 * runtime gate (`nativeBridgeCapabilities.pip`) alone would silently do nothing in a
 * generated APK. Exports that enable PiP patch the existing attribute in place instead
 * of inserting a new one.
 */
class AxmlRebuilderPictureInPictureTest {

    private val rebuilder = AxmlRebuilder()

    private fun loadTemplateManifest(): ByteArray? {
        val template = File("src/main/assets/template/webview_shell.apk")
        if (!template.exists()) return null
        return ZipFile(template).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return null
            zip.getInputStream(entry).readBytes()
        }
    }

    private fun activityElement(axml: ByteArray, name: String): Resources.XmlElement? {
        val root = AxmlToProtoXml.convert(axml).element

        // Activities sit under <application>, so walk the whole tree instead of only
        // the manifest's direct children.
        fun find(element: Resources.XmlElement): Resources.XmlElement? {
            if (element.name == "activity" &&
                element.attributeList.any { it.name == "name" && it.value == name }
            ) {
                return element
            }
            for (child in element.childList) {
                if (!child.hasElement()) continue
                find(child.element)?.let { return it }
            }
            return null
        }

        return find(root)
    }

    /** `null` when the activity or the attribute is absent from the manifest. */
    private fun supportsPictureInPicture(axml: ByteArray, name: String): Boolean? {
        val activity = activityElement(axml, name) ?: return null
        val attr = activity.attributeList.firstOrNull {
            it.name == "supportsPictureInPicture"
        } ?: return null
        assertThat(attr.compiledItem.hasPrim()).isTrue()
        return attr.compiledItem.prim.booleanValue
    }

    private fun rewritten(original: ByteArray, pkg: String, pip: Boolean) =
        rebuilder.expandAndModifyFull(
            original,
            originalPackage = "com.webtoapp",
            newPackage = pkg,
            versionCode = 1,
            versionName = "1.0.0",
            pictureInPictureEnabled = pip
        )

    @Test
    fun `template declares ShellActivity PiP opt-out`() {
        val original = loadTemplateManifest()
        assumeTrue(
            "shell template not built — run :app:syncShellTemplateApk first",
            original != null
        )

        // The patch path requires the attribute to exist; a template without it makes
        // the export silently skip PiP, so fail loudly here instead.
        assertThat(supportsPictureInPicture(original!!, "com.webtoapp.ui.shell.ShellActivity"))
            .isFalse()
    }

    @Test
    fun `pictureInPictureEnabled flips ShellActivity attribute to true`() {
        val original = loadTemplateManifest()
        assumeTrue(
            "shell template not built — run :app:syncShellTemplateApk first",
            original != null
        )

        val after = rewritten(original!!, "com.example.pip", pip = true)

        assertThat(supportsPictureInPicture(after, "com.webtoapp.ui.shell.ShellActivity"))
            .isTrue()
    }

    @Test
    fun `disabled picture-in-picture leaves ShellActivity opt-out intact`() {
        val original = loadTemplateManifest()
        assumeTrue(
            "shell template not built — run :app:syncShellTemplateApk first",
            original != null
        )

        val after = rewritten(original!!, "com.example.nopip", pip = false)

        assertThat(supportsPictureInPicture(after, "com.webtoapp.ui.shell.ShellActivity"))
            .isFalse()
    }

    @Test
    fun `PiP rewrite is idempotent across two passes`() {
        val original = loadTemplateManifest()
        assumeTrue(
            "shell template not built — run :app:syncShellTemplateApk first",
            original != null
        )

        val once = rewritten(original!!, "com.example.piponce", pip = true)
        val twice = rewritten(once, "com.example.piptwice", pip = true)

        assertThat(supportsPictureInPicture(twice, "com.webtoapp.ui.shell.ShellActivity"))
            .isTrue()
    }

    @Test
    fun `PiP opt-in does not leak onto other activities`() {
        val original = loadTemplateManifest()
        assumeTrue(
            "shell template not built — run :app:syncShellTemplateApk first",
            original != null
        )

        val after = rewritten(original!!, "com.example.pipscope", pip = true)

        // MainActivity is rewired to exported=false by the same pass; PiP must stay off
        // it (it declares no attribute, so the patch path never touches it).
        assertThat(supportsPictureInPicture(after, "com.webtoapp.ui.MainActivity"))
            .isNull()
    }
}
