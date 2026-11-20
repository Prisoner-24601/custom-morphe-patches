package app.keyboardspeed.patches.youtube.captions

import app.keyboardspeed.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/keyboardspeed/extension/MonitorCaptionSizePatch;"

/**
 * Sets the font size of the captions, which all caption renderers of the app use.
 */
internal object CaptionWindowFontSizeFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/player/subtitles/ui/SubtitleWindowView;",
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Math;->abs(F)F"),
        fieldAccess(opcode = Opcode.IPUT, definingClass = "this", type = "F"),
    ),
)

@Suppress("unused")
val monitorCaptionSizePatch = bytecodePatch(
    name = "Monitor caption size",
    description = "Shows the captions smaller while the app is on a monitor, such as with Samsung DeX or a " +
        "USB-C dock, and at their usual size on the screen of the phone.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    extendWith("extensions/extension.mpe")

    val captionSize by intOption(
        key = "monitorCaptionSize",
        default = 75,
        values = mapOf(
            "50%" to 50,
            "60%" to 60,
            "67%" to 67,
            "75%" to 75,
            "80%" to 80,
            "90%" to 90,
        ),
        title = "Caption size on a monitor",
        description = "Size of the captions on a monitor, in percent of their size on the phone. " +
            "It's the same as the caption size setting of Morphe, but only on a monitor.",
        required = true,
    ) { it != null && it in 10..200 }

    execute {
        CaptionWindowFontSizeFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range { p1 .. p1 }, $EXTENSION_CLASS->scaleFontSize(F)F
                move-result p1
            """,
        )

        mutableClassDefBy(EXTENSION_CLASS).methods.first { it.name == "getMonitorCaptionSize" }.addInstructions(
            0,
            """
                const/16 v0, $captionSize
                return v0
            """,
        )
    }
}
