package dev.dlrevive.integrations;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Adds a single "dlrevive" entry to the Library options menu that opens the app's Settings screen,
 * where the dlrevive section (downloads list, toggles, version/status) already lives.
 *
 * <p>Pure-Java decision helpers ({@link #shouldAdd(boolean)}, {@link #ITEM_ID}) are kept Android-free
 * so the duplicate guard can be verified with JVM unit tests.</p>
 */
public final class LibraryEntry {

    private static final String TAG = "dlrevive";

    /** Synthetic, app-private item id that cannot collide with YTM's resource-generated ids. */
    public static final int ITEM_ID = 0x7f0bfffe;

    private static final String SETTINGS_ACTIVITY =
            "com.google.android.apps.youtube.music.settings.SettingsCompatActivity";

    private LibraryEntry() {
    }

    /** True only when no dlrevive item is present yet (onCreateOptionsMenu may fire repeatedly). */
    public static boolean shouldAdd(boolean itemAlreadyPresent) {
        return !itemAlreadyPresent;
    }

    /** YTM's own single-track preview player, exported and able to open a content Uri. */
    public static final String PREVIEW_PLAYER_ACTIVITY =
            "com.google.android.apps.youtube.music.audiopreview.AudioPreviewPlayerActivity";

    /**
     * Called from the injected hook with the fragment (v1) and its Menu (v2). Never throws into
     * the app: any failure simply leaves the menu unchanged.
     */
    public static void install(Context ctx, Menu menu) {
        if (ctx == null || menu == null) {
            return;
        }
        try {
            boolean alreadyPresent = menu.findItem(ITEM_ID) != null;
            if (!shouldAdd(alreadyPresent)) {
                return;
            }
            MenuItem item = menu.add(0, ITEM_ID, 0, "dlrevive");
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem clicked) {
                    try {
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName(ctx.getPackageName(), SETTINGS_ACTIVITY));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(intent);
                        return true;
                    } catch (Throwable t) {
                        Log.w(TAG, "dlrevive: could not open settings from Library", t);
                        return false;
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "dlrevive: could not add Library entry", t);
        }
    }
}
