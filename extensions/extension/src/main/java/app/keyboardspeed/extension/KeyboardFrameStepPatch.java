package app.keyboardspeed.extension;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Steps the video one frame back or forward with the , and . keys of a hardware keyboard,
 * like on the YouTube website. Holding a key repeats the step. The video is paused first.
 */
@SuppressWarnings("unused")
public final class KeyboardFrameStepPatch {
    private static final String TAG = "KeyboardFrameStep";

    /**
     * Frame rate used if the frame rate of the video is not known, such as for a quality named "1080p".
     */
    private static final int DEFAULT_FRAME_RATE = 30;

    /**
     * Frame rate in the name of a video quality, such as the 60 of "1080p60".
     */
    private static final Pattern QUALITY_FRAME_RATE = Pattern.compile("\\d{3,4}p(\\d{2,3})");

    /**
     * Steps less than this apart continue from the time of the previous step, instead of the time
     * the player reports, which can lag behind the seeks while a key is held.
     */
    private static final long CONTINUE_STEPS_MS = 1000;

    /**
     * Shortest time between the steps of a held key, so the player keeps up with the seeks.
     */
    private static final long MINIMUM_REPEAT_MS = 40;

    private static double targetTime = -1;
    private static long lastStepTime;

    private KeyboardFrameStepPatch() {
    }

    /**
     * Frame rate chosen in the patch options, or 0 for the frame rate of the video quality.
     * The body is replaced at patch time.
     */
    private static int getFrameRate() {
        return 0;
    }

    /**
     * @return Whether the event was consumed.
     */
    static boolean handleKey(Activity activity, KeyEvent event) {
        final int direction;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_COMMA:
                direction = -1;
                break;
            case KeyEvent.KEYCODE_PERIOD:
                direction = 1;
                break;
            default:
                return false;
        }

        // Shift gives < and >, which keep their default action.
        if (event.isShiftPressed() || !Morphe.isPlayerOpen()) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            final long now = SystemClock.uptimeMillis();
            if (event.getRepeatCount() == 0 || now - lastStepTime >= MINIMUM_REPEAT_MS) {
                step(activity, direction, now);
            }
        }
        return true;
    }

    private static void step(Activity activity, int direction, long now) {
        if (!"PAUSED".equals(Morphe.getVideoState())) {
            pause(activity);
        }

        if (targetTime < 0 || now - lastStepTime > CONTINUE_STEPS_MS) {
            final long videoTime = Morphe.getVideoTime();
            if (videoTime < 0) {
                return;
            }
            targetTime = videoTime;
        }

        targetTime = Math.max(0, targetTime + direction * 1000.0 / frameRate());
        lastStepTime = now;
        if (!Morphe.seekTo(Math.round(targetTime))) {
            // Such as at the end of the video, or while the video is loading.
            targetTime = -1;
        }
    }

    private static int frameRate() {
        final int chosen = getFrameRate();
        if (chosen > 0) {
            return chosen;
        }

        String quality = Morphe.getQualityName();
        if (quality != null) {
            Matcher matcher = QUALITY_FRAME_RATE.matcher(quality);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return DEFAULT_FRAME_RATE;
    }

    /**
     * Pauses the video the same as the pause key of a keyboard or headset, which does nothing if already paused.
     */
    private static void pause(Activity activity) {
        try {
            AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
        } catch (Exception ex) {
            Log.e(TAG, "pause failure", ex);
        }
    }
}
