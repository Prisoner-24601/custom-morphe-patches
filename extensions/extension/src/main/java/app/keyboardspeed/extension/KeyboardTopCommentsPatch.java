package app.keyboardspeed.extension;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Shows the Top comments of the opened video with a key of a hardware keyboard (K by default).
 * <p>
 * In the Timed view of the comments, the list follows the video, so the sort buttons at the top of the list
 * (Top, Timed, Newest) are hard to get back to. The key scrolls the comments to the top and presses the Top
 * button of the app. If the comments are closed, they are opened first.
 */
@SuppressWarnings("unused")
public final class KeyboardTopCommentsPatch {
    private static final String TAG = "KeyboardTopComments";

    /**
     * Text of the sort button, as the app shows it in English.
     */
    private static final String TOP_BUTTON_TEXT = "Top";

    private static final String RECYCLER_VIEW_CLASS = "androidx.recyclerview.widget.RecyclerView";

    /**
     * Time for the comments to open, when they were closed.
     */
    private static final long OPEN_COMMENTS_DELAY_MS = 800;

    /**
     * Time for the list to show its top after scrolling, before looking for the button.
     */
    private static final long SCROLL_DELAY_MS = 150;

    private static final int MAX_ATTEMPTS = 6;

    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Incremented by each key press, so the attempts of a previous press stop.
     */
    private static int request;

    private KeyboardTopCommentsPatch() {
    }

    /**
     * Key code of the shortcut.
     * The body is replaced at patch time with the key chosen in the patch options.
     */
    static int getKeyCode() {
        return KeyEvent.KEYCODE_K;
    }

    /**
     * @return Whether the event was consumed.
     */
    static boolean handleKey(Activity activity, KeyEvent event) {
        if (event.getKeyCode() != getKeyCode() || !Morphe.isPlayerOpen()) {
            return false;
        }

        if (KeyboardShortcutsPatch.isFirstPress(event)) {
            final int thisRequest = ++request;
            WeakReference<Activity> activityRef = new WeakReference<>(activity);
            if (KeyboardCommentsPatch.findShownPanelCloseButton(activity) == null) {
                KeyboardCommentsPatch.openComments(activity);
                handler.postDelayed(() -> showTop(activityRef, thisRequest, MAX_ATTEMPTS), OPEN_COMMENTS_DELAY_MS);
            } else {
                showTop(activityRef, thisRequest, MAX_ATTEMPTS);
            }
        }
        return true;
    }

    private static void showTop(WeakReference<Activity> activityRef, int thisRequest, int attemptsLeft) {
        Activity activity = activityRef.get();
        if (activity == null || thisRequest != request || activity.isFinishing()) {
            return;
        }
        try {
            View panel = findCommentsPanel(activity);
            if (panel != null) {
                // The sort buttons are at the top of the list, which may be scrolled away.
                scrollToTop(panel);
                handler.postDelayed(() -> {
                    Activity current = activityRef.get();
                    if (current == null || thisRequest != request) {
                        return;
                    }
                    View panelNow = findCommentsPanel(current);
                    if (panelNow != null && pressTopButton(panelNow)) {
                        Log.d(TAG, "Pressed the Top button of the comments");
                    } else {
                        retry(activityRef, thisRequest, attemptsLeft);
                    }
                }, SCROLL_DELAY_MS);
                return;
            }
        } catch (Exception ex) {
            Log.e(TAG, "showTop failure", ex);
        }
        retry(activityRef, thisRequest, attemptsLeft);
    }

    private static void retry(WeakReference<Activity> activityRef, int thisRequest, int attemptsLeft) {
        if (attemptsLeft > 1) {
            handler.postDelayed(() -> showTop(activityRef, thisRequest, attemptsLeft - 1), SCROLL_DELAY_MS);
            return;
        }
        Activity activity = activityRef.get();
        if (activity != null) {
            Log.w(TAG, "Top button of the comments not found");
            Toast.makeText(activity, "Top comments button not found", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @return The panel shown on screen, such as the comments: the closest ancestor of its close button
     *         that has a list, or null if no panel is shown.
     */
    private static View findCommentsPanel(Activity activity) {
        View closeButton = KeyboardCommentsPatch.findShownPanelCloseButton(activity);
        View view = closeButton;
        for (int level = 0; level < 15 && view != null; level++) {
            if (view instanceof ViewGroup group && findRecyclerView(group) != null) {
                return group;
            }
            view = view.getParent() instanceof View parent ? parent : null;
        }
        return null;
    }

    private static View findRecyclerView(View view) {
        if (isRecyclerView(view)) {
            return view;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findRecyclerView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isRecyclerView(View view) {
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(RECYCLER_VIEW_CLASS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scrolls the lists of the panel to their top, also stopping a scroll in progress.
     */
    private static void scrollToTop(View view) {
        if (!view.isShown()) {
            return;
        }
        if (isRecyclerView(view)) {
            try {
                view.getClass().getMethod("stopScroll").invoke(view);
                view.getClass().getMethod("scrollToPosition", int.class).invoke(view, 0);
            } catch (Exception ex) {
                Log.e(TAG, "scrollToTop failure", ex);
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                scrollToTop(group.getChildAt(i));
            }
        }
    }

    /**
     * Presses the Top sort button shown in the panel.
     *
     * @return If the button was found and pressed.
     */
    private static boolean pressTopButton(View view) {
        if (!view.isShown()) {
            return false;
        }
        CharSequence description = view.getContentDescription();
        CharSequence text = view instanceof TextView textView ? textView.getText() : null;
        if (isTopText(description) || isTopText(text)) {
            View clickable = view;
            while (clickable != null && !clickable.isClickable()) {
                clickable = clickable.getParent() instanceof View parent ? parent : null;
            }
            if (clickable != null && clickable.performClick()) {
                return true;
            }
        }
        // Buttons drawn by a component library without views of their own are only known to accessibility.
        if (pressAccessibilityTopButton(view)) {
            return true;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (pressTopButton(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean pressAccessibilityTopButton(View view) {
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        if (provider == null) {
            return false;
        }
        try {
            List<AccessibilityNodeInfo> nodes = provider.findAccessibilityNodeInfosByText(
                    TOP_BUTTON_TEXT, AccessibilityNodeProvider.HOST_VIEW_ID);
            if (nodes == null) {
                return false;
            }
            for (AccessibilityNodeInfo node : nodes) {
                if (!isTopText(node.getText()) && !isTopText(node.getContentDescription())) continue;
                // The id of the node within the view is the upper half of its source id.
                long sourceId = (long) AccessibilityNodeInfo.class.getMethod("getSourceNodeId").invoke(node);
                if (provider.performAction((int) (sourceId >> 32), AccessibilityNodeInfo.ACTION_CLICK, null)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            Log.d(TAG, "Accessibility press failed", ex);
        }
        return false;
    }

    private static boolean isTopText(CharSequence text) {
        return text != null && text.toString().trim().equalsIgnoreCase(TOP_BUTTON_TEXT);
    }
}
