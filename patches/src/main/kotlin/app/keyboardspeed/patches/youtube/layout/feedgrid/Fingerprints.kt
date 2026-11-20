package app.keyboardspeed.patches.youtube.layout.feedgrid

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

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
