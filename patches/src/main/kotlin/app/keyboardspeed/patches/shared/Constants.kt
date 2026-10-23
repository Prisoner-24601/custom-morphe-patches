package app.keyboardspeed.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_YOUTUBE = Compatibility(
        name = "YouTube",
        packageName = "com.google.android.youtube",
        // Must match the official Morphe YouTube patches.
        apkFileType = ApkFileType.APK_REQUIRED,
        appIconColor = 0xFF0033,
        targets = listOf(
            // Only hooks the unobfuscated MainActivity, so it follows whatever version
            // the Morphe YouTube patches it is applied with support.
            AppTarget(version = null),
        ),
    )
}
