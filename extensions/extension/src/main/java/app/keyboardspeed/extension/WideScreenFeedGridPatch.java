package app.keyboardspeed.extension;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Shows the feeds as a grid of videos on a monitor, such as with Samsung DeX or a USB-C dock,
 * instead of one giant video at a time.
 * <ul>
 *     <li>YouTube saves the configuration that its server sends, and the server sends a different configuration
 *     for a phone and for a tablet. Started on a monitor with the configuration of a phone, the home feed shows
 *     one video per row across the whole width. When the app starts on another kind of screen than the last time,
 *     the saved configuration is removed, so YouTube gets the configuration for the screen it's on.</li>
 *     <li>The requests to the server are of a tablet on a monitor, as YouTube can decide that it's a phone
 *     by the built-in display only.</li>
 *     <li>YouTube moved from the phone to a monitor while it's running keeps the configuration of the phone,
 *     so it's restarted (patch option).</li>
 *     <li>If the feed is larger than all layouts of the server, YouTube uses a single column,
 *     and the layout for the width is used instead.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class WideScreenFeedGridPatch {
    private static final String TAG = "WideScreenFeedGrid";

    /**
     * Form factors of the requests to the server.
     */
    private static final int FORM_FACTOR_PHONE = 1;
    private static final int FORM_FACTOR_TABLET = 2;

    /**
     * Screens at least this wide in both orientations are tablets, as in Android.
     */
    private static final int TABLET_SMALLEST_WIDTH_DP = 600;

    /**
     * Preferences of YouTube, with the saved configuration of the server.
     */
    private static final String YOUTUBE_PREFERENCES = "youtube";
    private static final String[] SERVER_CONFIGURATION_KEYS = {
            "com.google.android.libraries.youtube.innertube.cold_config_group",
            "com.google.android.libraries.youtube.innertube.hot_config_group",
            "com.google.android.libraries.youtube.innertube.cold_hash_data",
            "com.google.android.libraries.youtube.innertube.hot_hash_data",
            "com.google.android.libraries.youtube.innertube.cold_stored_timestamp",
            "com.google.android.libraries.youtube.innertube.hot_stored_timestamp",
    };

    private static final String PREFERENCES = "keyboardspeed_wide_screen_feed_grid";
    private static final String LAST_SCREEN_KEY = "last_screen";
    private static final String LAST_RESTART_KEY = "last_restart";
    private static final int SCREEN_PHONE = 0;
    private static final int SCREEN_MONITOR = 1;

    /**
     * Restarts closer together than this are not done, in case the screen keeps changing.
     */
    private static final long MIN_RESTART_INTERVAL_MS = 60_000;

    private static Field maxWidthField;
    private static Field maxHeightField;
    private static boolean fieldsLooked;

    private static int lastLoggedFormFactor = -1;

    /**
     * If the app started on a monitor, with the configuration of the server for a monitor.
     */
    private static boolean startedOnMonitor;

    /**
     * The activity in front, for restarting the app on the same screen.
     */
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);

    private WideScreenFeedGridPatch() {
    }

    /**
     * Patch option 'Restart when moved to a monitor'. The body is replaced at patch time.
     */
    private static boolean isRestartOnMonitorEnabled() {
        return false;
    }

    /**
     * Injection point. Called at the start of the creation of the app, before YouTube reads its configuration.
     */
    public static void onApplicationCreate(Context context) {
        try {
            startedOnMonitor = isMonitor(context.getResources().getConfiguration());
            final int screen = startedOnMonitor ? SCREEN_MONITOR : SCREEN_PHONE;

            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            final int lastScreen = preferences.getInt(LAST_SCREEN_KEY, -1);
            if (screen != lastScreen) {
                // The saved configuration is of another kind of screen, or of a version without this patch.
                SharedPreferences.Editor youtube = context.getSharedPreferences(
                        YOUTUBE_PREFERENCES, Context.MODE_PRIVATE).edit();
                for (String key : SERVER_CONFIGURATION_KEYS) {
                    youtube.remove(key);
                }
                youtube.commit();
                preferences.edit().putInt(LAST_SCREEN_KEY, screen).commit();
                Log.i(TAG, "Started on " + (startedOnMonitor ? "a monitor" : "the phone")
                        + ", removed the saved server configuration of the last screen");
            }

            if (isRestartOnMonitorEnabled() && context instanceof Application application) {
                watchScreen(application);
            }
        } catch (Exception ex) {
            Log.e(TAG, "onApplicationCreate failure", ex);
        }
    }

    /**
     * Injection point. Called for the requests to the server, on any thread.
     * <p>
     * YouTube can decide by the built-in display only if it's a phone or a tablet.
     * This uses the screen the app is on: a tablet on a monitor, a phone on the phone.
     * The 'Device layout' setting of the Morphe patches is applied after this, so its Phone and Tablet still apply.
     *
     * @param original The form factor that YouTube chose.
     */
    public static int getFormFactor(int original) {
        try {
            if (original != FORM_FACTOR_PHONE) {
                return original;
            }
            Context context = getContext();
            if (context == null) {
                return original;
            }
            Configuration configuration = context.getResources().getConfiguration();
            final int formFactor = isMonitor(configuration) ? FORM_FACTOR_TABLET : original;
            if (formFactor != lastLoggedFormFactor) {
                lastLoggedFormFactor = formFactor;
                Log.i(TAG, "Form factor " + formFactor + " for smallest width "
                        + configuration.smallestScreenWidthDp + "dp");
            }
            return formFactor;
        } catch (Exception ex) {
            Log.e(TAG, "getFormFactor failure", ex);
            return original;
        }
    }

    /**
     * Restarts the app when it moves from the phone to a monitor.
     */
    private static void watchScreen(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                resumedActivity = new WeakReference<>(activity);
                checkScreen(activity);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });

        application.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                Activity activity = resumedActivity.get();
                if (activity != null && isMonitor(newConfig)) {
                    // The configuration of the activity can change after the application.
                    new Handler(Looper.getMainLooper()).postDelayed(() -> checkScreen(activity), 500);
                }
            }

            @Override
            public void onLowMemory() {
            }
        });
    }

    /**
     * Restarts the app if it started on the phone and is now on a monitor.
     */
    private static void checkScreen(Activity activity) {
        try {
            if (startedOnMonitor || activity.isFinishing()
                    || !isMonitor(activity.getResources().getConfiguration())) {
                return;
            }

            SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            final long now = System.currentTimeMillis();
            if (Math.abs(now - preferences.getLong(LAST_RESTART_KEY, 0)) < MIN_RESTART_INTERVAL_MS) {
                return;
            }
            preferences.edit().putLong(LAST_RESTART_KEY, now).commit();
            startedOnMonitor = true;

            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
            if (intent == null) {
                return;
            }
            Log.i(TAG, "Moved from the phone to a monitor, restarting");
            // The new activity opens on the screen of the current activity.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finishAffinity();
            new Handler(Looper.getMainLooper()).postDelayed(() -> Runtime.getRuntime().exit(0), 300);
        } catch (Exception ex) {
            Log.e(TAG, "checkScreen failure", ex);
        }
    }

    /**
     * @return If the app is on a monitor or another large screen.
     */
    private static boolean isMonitor(Configuration configuration) {
        return configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
                || isSamsungDesktopMode(configuration);
    }

    /**
     * @return If the app is on the desktop of Samsung DeX.
     */
    private static boolean isSamsungDesktopMode(Configuration configuration) {
        try {
            Field enabled = Configuration.class.getField("semDesktopModeEnabled");
            Field enabledValue = Configuration.class.getField("SEM_DESKTOP_MODE_ENABLED");
            return enabled.getInt(configuration) == enabledValue.getInt(null);
        } catch (Exception ex) {
            return false; // Not a Samsung device.
        }
    }

    private static Context getContext() {
        try {
            Class<?> utils = Class.forName("app.morphe.extension.shared.Utils", true,
                    WideScreenFeedGridPatch.class.getClassLoader());
            Object context = utils.getMethod("getContext").invoke(null);
            if (context instanceof Context c) {
                return c;
            }
        } catch (Exception ignored) {
            // Use the application instead.
        }
        try {
            Object application = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return application instanceof Context c ? c : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Name of the field of the maximum width of a layout. The body is replaced at patch time.
     */
    private static String getMaxWidthFieldName() {
        return "";
    }

    /**
     * Name of the field of the maximum height of a layout. The body is replaced at patch time.
     */
    private static String getMaxHeightFieldName() {
        return "";
    }

    /**
     * Injection point. Called with the layout that YouTube chose for the size of a feed.
     *
     * @param layout  The layout YouTube chose for the size.
     * @param layouts The layouts from the server, for screens up to a maximum width and height.
     * @return The layout to use.
     */
    public static Object getLayout(Object layout, List<?> layouts, int width, int height) {
        try {
            if (layouts == null || layouts.isEmpty() || layouts.contains(layout)
                    || !lookUpFields(layouts.get(0).getClass())) {
                return layout;
            }

            // No layout of the server is for this size, and YouTube uses a single column.
            // Use the layout for the width, ignoring the height, or else the widest layout.
            Object forWidth = null;
            int forWidthMax = Integer.MAX_VALUE;
            Object widest = null;
            int widestMax = Integer.MIN_VALUE;
            for (Object candidate : layouts) {
                final int maxWidth = maxWidthField.getInt(candidate);
                if (maxWidth >= width && maxWidth < forWidthMax) {
                    forWidth = candidate;
                    forWidthMax = maxWidth;
                }
                if (maxWidth > widestMax) {
                    widest = candidate;
                    widestMax = maxWidth;
                }
            }
            Object chosen = forWidth != null ? forWidth : widest;
            if (chosen == null) {
                return layout;
            }
            Log.i(TAG, "No layout for " + width + "x" + height + ", using the layout up to "
                    + maxWidthField.getInt(chosen) + "x" + maxHeightField.getInt(chosen));
            return chosen;
        } catch (Exception ex) {
            Log.e(TAG, "getLayout failure", ex);
            return layout;
        }
    }

    private static synchronized boolean lookUpFields(Class<?> layoutClass) {
        if (!fieldsLooked) {
            fieldsLooked = true;
            try {
                maxWidthField = layoutClass.getDeclaredField(getMaxWidthFieldName());
                maxWidthField.setAccessible(true);
                maxHeightField = layoutClass.getDeclaredField(getMaxHeightFieldName());
                maxHeightField.setAccessible(true);
            } catch (Exception ex) {
                Log.e(TAG, "Layout fields not found", ex);
                maxWidthField = null;
            }
        }
        return maxWidthField != null;
    }
}
