package app.keyboardspeed.extension;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Shows and hides the comments panel of the opened video with a key of a hardware keyboard.
 * <p>
 * The panel is shown with the same command that the comments button of the app runs,
 * through the command router of the official Morphe YouTube patches.
 */
@SuppressWarnings("unused")
public final class KeyboardCommentsPatch {
    private static final String TAG = "KeyboardComments";

    /**
     * Ids of the comments panel of the watch page. Newer app versions use the first id.
     */
    private static final List<String> COMMENTS_PANEL_IDS = List.of("comment-item-section", "engagement-panel-comments-section");

    private static final int SHOW_ENGAGEMENT_PANEL_ENDPOINT_FIELD = 138681778;
    private static final int SHOW_ENGAGEMENT_PANEL_ENDPOINT_PANEL_ID_FIELD = 1;

    /**
     * Time to wait for the panel to open, before trying the panel id of other app versions.
     */
    private static final long PANEL_OPEN_CHECK_DELAY_MS = 500;

    /**
     * A panel shown on screen is at least as tall as its header.
     */
    private static final int MINIMUM_PANEL_HEIGHT_DP = 48;

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private KeyboardCommentsPatch() {
    }

    /**
     * Key code of the shortcut.
     * The body is replaced at patch time with the key chosen in the patch options.
     */
    static int getKeyCode() {
        return KeyEvent.KEYCODE_M;
    }

    /**
     * @return Whether the event was consumed.
     */
    static boolean handleKey(Activity activity, KeyEvent event) {
        if (event.getKeyCode() != getKeyCode() || !Morphe.isPlayerOpen()) {
            return false;
        }

        if (KeyboardShortcutsPatch.isFirstPress(event)) {
            toggleComments(activity);
        }
        return true;
    }

    private static void toggleComments(Activity activity) {
        // Morphe does not record every closing of a panel, so it can still list the comments as open
        // after they were closed. Back would then minimize the video instead, so back is only used
        // if a panel is also shown on screen. Showing the comments again while they are open does nothing.
        if (!Boolean.FALSE.equals(Morphe.isPanelOpen(COMMENTS_PANEL_IDS)) && isPanelOnScreen(activity)) {
            // Back closes the panel, the same as the back button.
            activity.onBackPressed();
            return;
        }

        if (!showPanel(COMMENTS_PANEL_IDS.get(0))) {
            Toast.makeText(activity, "Comments shortcut needs Morphe Patches 2.2 or newer", Toast.LENGTH_SHORT).show();
            return;
        }

        // Other app versions use another panel id. Showing a panel the video does not have does nothing.
        handler.postDelayed(() -> {
            String lastPanel = Morphe.getLastOpenedPanelId();
            Boolean commentsOpen = Morphe.isPanelOpen(COMMENTS_PANEL_IDS);
            if (commentsOpen != null && !commentsOpen && !COMMENTS_PANEL_IDS.contains(lastPanel)) {
                Log.d(TAG, "Comments panel did not open, trying: " + COMMENTS_PANEL_IDS.get(1));
                showPanel(COMMENTS_PANEL_IDS.get(1));
            }
        }, PANEL_OPEN_CHECK_DELAY_MS);
    }

    /**
     * @return If the engagement panel container of the activity shows a panel on screen.
     *         False if the container is not found, so back is never sent without a shown panel.
     */
    @SuppressLint("DiscouragedApi")
    private static boolean isPanelOnScreen(Activity activity) {
        int id = activity.getResources().getIdentifier("app_engagement_panel", "id", activity.getPackageName());
        View container = id == 0 ? null : activity.findViewById(id);
        if (!(container instanceof ViewGroup group) || !group.isShown()) {
            return false;
        }

        // The container can be shown while its panels are hidden or slid off screen.
        final float minimumHeight = MINIMUM_PANEL_HEIGHT_DP * activity.getResources().getDisplayMetrics().density;
        Rect visible = new Rect();
        for (int i = 0; i < group.getChildCount(); i++) {
            View panel = group.getChildAt(i);
            if (panel.isShown() && panel.getGlobalVisibleRect(visible)
                    && visible.width() > 0 && visible.height() >= minimumHeight) {
                return true;
            }
        }
        return false;
    }

    private static boolean showPanel(String panelId) {
        return Morphe.executeCommand(showEngagementPanelCommand(panelId));
    }

    /**
     * @return The serialized command { showEngagementPanelEndpoint { panelIdentifier: panelId } }.
     */
    private static byte[] showEngagementPanelCommand(String panelId) {
        ByteArrayOutputStream endpoint = new ByteArrayOutputStream();
        writeLengthDelimited(endpoint, SHOW_ENGAGEMENT_PANEL_ENDPOINT_PANEL_ID_FIELD,
                panelId.getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream command = new ByteArrayOutputStream();
        writeLengthDelimited(command, SHOW_ENGAGEMENT_PANEL_ENDPOINT_FIELD, endpoint.toByteArray());
        return command.toByteArray();
    }

    private static void writeLengthDelimited(ByteArrayOutputStream output, int field, byte[] value) {
        writeVarint(output, ((long) field << 3) | 2);
        writeVarint(output, value.length);
        output.write(value, 0, value.length);
    }

    private static void writeVarint(ByteArrayOutputStream output, long value) {
        while ((value & ~0x7FL) != 0) {
            output.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        output.write((int) value);
    }
}
