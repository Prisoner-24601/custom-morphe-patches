package app.keyboardspeed.patches.youtube.layout.feedgrid

import app.keyboardspeed.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

private const val EXTENSION_CLASS = "Lapp/keyboardspeed/extension/WideScreenFeedGridPatch;"

/**
 * Copy of the unchanged method that chooses the layout, which the changed method calls.
 */
private const val LAYOUT_FOR_SIZE_ORIGINAL = "patch_layoutForSizeOriginal"

@Suppress("unused")
val wideScreenFeedGridPatch = bytecodePatch(
    name = "Wide screen feed grid",
    description = "Shows the home feed and other feeds as a grid on screens that are wider than the " +
        "grid layouts of YouTube, such as an external monitor, instead of one giant video at a time.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    extendWith("extensions/extension.mpe")

    execute {
        val classDef = FeedGridLayoutForSizeFingerprint.classDef
        val method = FeedGridLayoutForSizeFingerprint.method
        val layoutType = method.returnType

        // The layouts from the server, and the maximum width and height of a layout,
        // which are the first fields of the layout read by the method.
        val fields = method.instructions
            .filter { it.opcode == Opcode.IGET_OBJECT || it.opcode == Opcode.IGET }
            .map { (it as ReferenceInstruction).reference as FieldReference }
        val layoutsField = fields.firstOrNull { it.definingClass == classDef.type && it.type.startsWith("L") }
            ?: throw PatchException("Feed grid layouts field not found")
        val sizeFields = fields.filter { it.definingClass == layoutType && it.type == "I" }.distinct()
        if (sizeFields.size < 2) {
            throw PatchException("Feed grid layout size fields not found")
        }
        val (maxWidthField, maxHeightField) = sizeFields

        // Field names of the obfuscated layout class, for the extension.
        mapOf(
            "getMaxWidthFieldName" to maxWidthField.name,
            "getMaxHeightFieldName" to maxHeightField.name,
        ).forEach { (extensionMethod, fieldName) ->
            mutableClassDefBy(EXTENSION_CLASS).methods.first { it.name == extensionMethod }.addInstructions(
                0,
                """
                    const-string v0, "$fieldName"
                    return-object v0
                """,
            )
        }

        // Keep the original code in a copy, and pass its layout to the extension.
        val implementation = method.implementation!!
        classDef.methods.add(
            ImmutableMethod(
                classDef.type,
                LAYOUT_FOR_SIZE_ORIGINAL,
                method.parameters,
                method.returnType,
                AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
                null,
                null,
                ImmutableMethodImplementation(
                    implementation.registerCount,
                    implementation.instructions,
                    implementation.tryBlocks,
                    implementation.debugItems,
                ),
            ).toMutable(),
        )

        // Parameters are copied to low registers, as the method has more than 16 registers.
        if (implementation.registerCount - 3 < 5) {
            throw PatchException("Feed grid layout method has too few registers")
        }
        // The original code after the inserted code is no longer reached.
        method.addInstructions(
            0,
            """
                move-object/from16 v2, p0
                move/from16 v3, p1
                move/from16 v4, p2
                invoke-direct { v2, v3, v4 }, ${classDef.type}->$LAYOUT_FOR_SIZE_ORIGINAL(II)$layoutType
                move-result-object v0
                iget-object v1, v2, $layoutsField
                invoke-static { v0, v1, v3, v4 }, $EXTENSION_CLASS->getLayout(Ljava/lang/Object;Ljava/util/List;II)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, $layoutType
                return-object v0
            """,
        )
    }
}
