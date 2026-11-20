package app.keyboardspeed.extension;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Shows the captions smaller while the app is on a monitor, such as with Samsung DeX or a USB-C dock.
 * The captions of the app grow with the size of the player, so on a monitor they are much larger than on a phone.
 * The screen is checked each time a caption is laid out, so the size follows the app between the phone and
 * the monitor without a restart.
 */
@SuppressWarnings("unused")
public final class MonitorCaptionSizePatch {
    private static final String TAG = "MonitorCaptionSize";

    /**
     * Screens at least this wide in both orientations are tablets and monitors, as in Android.
     */
    private static final int MONITOR_SMALLEST_WIDTH_DP = 600;

    private static Method getActivity;
    private static boolean lookedUpActivity;

    private static Field desktopModeEnabled;
    private static int desktopModeEnabledValue;
    private static boolean lookedUpDesktopMode;

    private static Boolean lastLoggedMonitor;

    private MonitorCaptionSizePatch() {
    }

    /**
     * Patch option 'Caption size on a monitor', in percent. The body is replaced at patch time.
     */
    private static int getMonitorCaptionSize() {
        return 100;
    }

    /**
     * Injection point. Called with the font size of the captions.
     */
    public static float scaleFontSize(float size) {
        try {
            final boolean monitor = isOnMonitor();
            if (lastLoggedMonitor == null || lastLoggedMonitor != monitor) {
                lastLoggedMonitor = monitor;
                Log.i(TAG, monitor ? "On a monitor, captions at " + getMonitorCaptionSize() + "%"
                        : "On the phone, captions at their usual size");
            }
            return monitor ? size * getMonitorCaptionSize() / 100f : size;
        } catch (Exception ex) {
            Log.e(TAG, "scaleFontSize failure", ex);
            return size;
        }
    }

    private static boolean isOnMonitor() throws IllegalAccessException {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        Configuration configuration = context.getResources().getConfiguration();
        return configuration.smallestScreenWidthDp >= MONITOR_SMALLEST_WIDTH_DP || isSamsungDesktopMode(configuration);
    }

    /**
     * @return If the app is on the desktop of Samsung DeX.
     */
    private static boolean isSamsungDesktopMode(Configuration configuration) throws IllegalAccessException {
        if (!lookedUpDesktopMode) {
            lookedUpDesktopMode = true;
            try {
                desktopModeEnabled = Configuration.class.getField("semDesktopModeEnabled");
                desktopModeEnabledValue = Configuration.class.getField("SEM_DESKTOP_MODE_ENABLED").getInt(null);
            } catch (Exception ex) {
                desktopModeEnabled = null; // Not a Samsung device.
            }
        }
        return desktopModeEnabled != null && desktopModeEnabled.getInt(configuration) == desktopModeEnabledValue;
    }

    /**
     * @return The activity in front, whose configuration is of the screen it's on, or null.
     */
    private static Context getContext() {
        if (!lookedUpActivity) {
            lookedUpActivity = true;
            try {
                getActivity = Class.forName("app.morphe.extension.shared.Utils", true,
                        MonitorCaptionSizePatch.class.getClassLoader()).getMethod("getActivity");
            } catch (Exception ex) {
                Log.w(TAG, "Morphe utilities not found", ex);
            }
        }
        try {
            return getActivity == null ? null : (Context) getActivity.invoke(null);
        } catch (Exception ex) {
            return null;
        }
    }
}
