package app.keyboardspeed.extension;

import android.app.Activity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Hardware keyboard shortcuts of the video player: playback speed, comments and fullscreen.
 * <p>
 * Each shortcut is enabled by its own patch, which changes the matching method below at patch time.
 * The shortcuts are also listed in the keyboard shortcuts list of the system,
 * which is shown by holding Ctrl or with Meta + /, depending on the device.
 */
@SuppressWarnings("unused")
public final class KeyboardShortcutsPatch {
    private static final String TAG = "KeyboardShortcuts";

    private KeyboardShortcutsPatch() {
    }

    // region Changed at patch time by the patch of each shortcut.

    private static boolean isSpeedEnabled() {
        return false;
    }

    private static boolean isCommentsEnabled() {
        return false;
    }

    private static boolean isFullscreenEnabled() {
        return false;
    }

    // endregion

    /**
     * Injection point.
     *
     * @return Whether the event was consumed.
     */
    public static boolean dispatchKeyEvent(Activity activity, KeyEvent event) {
        try {
            // Leave shortcuts such as Ctrl+= and any text input (search, comments) alone.
            if (event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()
                    || activity.getCurrentFocus() instanceof EditText) {
                return false;
            }

            return (isSpeedEnabled() && KeyboardSpeedPatch.handleKey(activity, event))
                    || (isCommentsEnabled() && KeyboardCommentsPatch.handleKey(activity, event))
                    || (isFullscreenEnabled() && KeyboardFullscreenPatch.handleKey(activity, event));
        } catch (Exception ex) {
            Log.e(TAG, "dispatchKeyEvent failure", ex);
            return false;
        }
    }

    /**
     * Injection point.
     * Adds the shortcuts to the keyboard shortcuts list of the system.
     */
    public static void onProvideKeyboardShortcuts(Activity activity, List<KeyboardShortcutGroup> data) {
        try {
            if (data == null) {
                return;
            }

            List<KeyboardShortcutInfo> shortcuts = new ArrayList<>();
            if (isSpeedEnabled()) {
                shortcuts.add(new KeyboardShortcutInfo("Decrease playback speed", KeyEvent.KEYCODE_MINUS, 0));
                shortcuts.add(new KeyboardShortcutInfo("Increase playback speed", KeyEvent.KEYCODE_EQUALS, 0));
            }
            if (isCommentsEnabled()) {
                shortcuts.add(new KeyboardShortcutInfo("Show or hide comments", KeyboardCommentsPatch.getKeyCode(), 0));
            }
            if (isFullscreenEnabled()) {
                shortcuts.add(new KeyboardShortcutInfo("Enter or exit fullscreen", KeyboardFullscreenPatch.getKeyCode(), 0));
            }

            if (!shortcuts.isEmpty()) {
                data.add(new KeyboardShortcutGroup("Video player (Morphe)", shortcuts));
            }
        } catch (Exception ex) {
            Log.e(TAG, "onProvideKeyboardShortcuts failure", ex);
        }
    }

    /**
     * @return If the event is the key, and acts only on the first press, not on its release or repeats.
     */
    static boolean isFirstPress(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
    }
}
