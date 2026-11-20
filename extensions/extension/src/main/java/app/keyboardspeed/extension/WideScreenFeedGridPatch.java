package app.keyboardspeed.extension;

import android.util.Log;

import java.lang.reflect.Field;
import java.util.List;

/**
 * YouTube lays out a feed as a grid with the layout that the server sends for the size of the feed,
 * such as 3 columns for a tablet. If the feed is larger than all of these layouts, such as on an
 * external monitor, YouTube uses a built-in layout with a single column, which shows one giant video
 * at a time. This uses the layout for the width instead, or else the widest layout.
 */
@SuppressWarnings("unused")
public final class WideScreenFeedGridPatch {
    private static final String TAG = "WideScreenFeedGrid";

    private static Field maxWidthField;
    private static Field maxHeightField;
    private static boolean fieldsLooked;

    private WideScreenFeedGridPatch() {
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
     * Injection point.
     *
     * @param layout  The layout YouTube chose for the size.
     * @param layouts The layouts from the server, for screens up to a maximum width and height.
     * @return The layout to use.
     */
    public static Object getLayout(Object layout, List<?> layouts, int width, int height) {
        try {
            if (layouts == null || layouts.isEmpty() || layouts.contains(layout)) {
                return layout;
            }
            if (!lookUpFields(layouts.get(0).getClass())) {
                return layout;
            }

            // The layout for the width, ignoring the height, or else the widest layout.
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

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                StringBuilder sizes = new StringBuilder();
                for (Object candidate : layouts) {
                    sizes.append(' ').append(maxWidthField.getInt(candidate))
                            .append('x').append(maxHeightField.getInt(candidate));
                }
                Log.d(TAG, "No layout for " + width + "x" + height + ", layouts:" + sizes
                        + ", using " + maxWidthField.getInt(chosen) + "x" + maxHeightField.getInt(chosen));
            }
            return chosen == null ? layout : chosen;
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
                maxHeightField = null;
            }
        }
        return maxWidthField != null && maxHeightField != null;
    }
}
