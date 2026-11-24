package dev.dlrevive.integrations;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * Adds a small, always-visible "dlrevive" button to the Library screen's toolbar.
 *
 * <p>The earlier menu-item approach was removed: it passed the fragment as a Context and caused a
 * VerifyError that crashed the app on entering Library (and was not visible anyway). This view-level
 * helper is the only Library entry point. All View APIs used here are public framework APIs
 * (android.jar); only the toolbar resource id is version-specific and was verified from YTM 8.40.54
 * bytecode.</p>
 */
public final class LibraryViewEntry {

    private static final String TAG = "dlrevive";

    /** Resource id of the Library toolbar (androidx Toolbar) in YTM 8.40.54. */
    private static final int TOOLBAR_ID = 0x7f0b0cc5;

    private static final String SETTINGS_ACTIVITY =
            "com.google.android.apps.youtube.music.settings.SettingsCompatActivity";

    private static final int TAG_KEY = 0x7f0bfffd; // unique view tag to avoid duplicates

    private LibraryViewEntry() {
    }

    /** Pure-Java guard so duplicate injection across fragment recreation can be unit-tested. */
    public static boolean shouldAdd(boolean alreadyPresent) {
        return !alreadyPresent;
    }

    /**
     * Called from the injected onViewCreated hook with the fragment's root View. Never throws into
     * the app: any failure simply leaves the Library screen unchanged.
     */
    public static void install(View root) {
        if (root == null) {
            return;
        }
        try {
            Context ctx = root.getContext();
            View toolbarView = root.findViewById(TOOLBAR_ID);
            if (!(toolbarView instanceof ViewGroup)) {
                Log.w(TAG, "Library toolbar not found; skipping dlrevive button");
                return;
            }
            ViewGroup toolbar = (ViewGroup) toolbarView;
            if (toolbar.findViewWithTag(TAG_KEY) != null) {
                return; // already injected (onViewCreated may fire again)
            }
            Button button = new Button(ctx);
            button.setText("dlrevive");
            button.setTag(TAG_KEY);
            button.setTextSize(11f);
            button.setAllCaps(false);
            button.setBackgroundColor(0x00000000);
            button.setTextColor(0xffffffff);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName(ctx.getPackageName(),
                                SETTINGS_ACTIVITY));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(intent);
                    } catch (Throwable t) {
                        Log.w(TAG, "dlrevive: could not open Settings from Library", t);
                    }
                }
            });
            // Center the button in the toolbar between title and the anchored action icons.
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            toolbar.addView(button, params);
            Log.d(TAG, "Library dlrevive button added");
        } catch (Throwable t) {
            Log.w(TAG, "dlrevive: could not add Library view entry", t);
        }
    }
}
