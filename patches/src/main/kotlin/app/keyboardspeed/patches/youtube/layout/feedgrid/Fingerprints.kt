package app.keyboardspeed.patches.youtube.layout.feedgrid

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Static initializer of the enum of the form factors that the app sends to the server, such as phone or tablet.
 */
internal object FormFactorEnumConstructorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.CONSTRUCTOR),
    strings = listOf(
        "UNKNOWN_FORM_FACTOR",
        "SMALL_FORM_FACTOR",
        "LARGE_FORM_FACTOR",
        "AUTOMOTIVE_FORM_FACTOR",
    ),
)

/**
 * onCreate of the application class of YouTube, before the app reads its saved configuration.
 */
internal object ApplicationOnCreateFingerprint : Fingerprint(
    name = "onCreate",
    returnType = "V",
    parameters = listOf(),
    strings = listOf("Application.onCreate", "Application creation"),
)

/**
 * @return If the instruction reads the number of a form factor, such as 1 for a phone and 2 for a tablet.
 */
internal fun isFormFactorNumberRead(instruction: Instruction, formFactorType: String) =
    instruction.opcode == Opcode.IGET &&
        ((instruction as ReferenceInstruction).reference as FieldReference).let {
            it.type == "I" && it.definingClass == formFactorType
        }

/**
 * Builds the client info of the requests to the server: the device model, the form factor and more.
 * The same method is hooked by the 'Device layout' setting of the Morphe patches.
 */
internal class InnerTubeClientConfigFingerprint(formFactorType: String) : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(smali = "Landroid/os/Build;->MODEL:Ljava/lang/String;"),
        fieldAccess(
            definingClass = formFactorType,
            type = "I",
            opcode = Opcode.IGET,
            location = InstructionLocation.MatchAfterWithin(50),
        ),
    ),
)

/**
 * Constructor of the class that chooses the grid layout of a feed, such as the number of columns,
 * from the layouts for each screen size that the server sends.
 */
internal object FeedGridLayoutConstructorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    strings = listOf("responsive_signal_block_without_key"),
)

/**
 * Returns the grid layout for a width and height: the first layout of the server whose maximum
 * width and height are not smaller, or else a built-in layout with a single column.
 */
internal object FeedGridLayoutForSizeFingerprint : Fingerprint(
    classFingerprint = FeedGridLayoutConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("I", "I"),
)
