/*
 * Compile-only compatibility signature for APIs removed from modern android.jar.
 * This class is placed on javac's boot class path and is never packaged in the APK.
 */
package android.app;

import android.content.Context;

public class Notification {
    public static final int DEFAULT_LIGHTS = 4;
    public static final int FLAG_AUTO_CANCEL = 16;

    public int defaults;
    public int flags;

    public Notification(int icon, CharSequence tickerText, long when) {
    }

    public void setLatestEventInfo(Context context, CharSequence contentTitle,
            CharSequence contentText, PendingIntent contentIntent) {
    }
}
