package dev.dlrevive.integrations;

/**
 * Constants for the Library-area entry point.
 *
 * <p>The menu-item approach was removed: it passed the fragment as a Context and triggered a
 * VerifyError that crashed the app on entering Library. The visible entry is now a toolbar Button
 * added by {@link LibraryViewEntry}. This class is retained only for the preview-player activity
 * name used by {@link SettingsUiBuilder} when opening a downloaded file in YTM's own player.</p>
 */
public final class LibraryEntry {

    /** YTM's exported single-track preview player (accepts ACTION_VIEW + content Uri). */
    public static final String PREVIEW_PLAYER_ACTIVITY =
            "com.google.android.apps.youtube.music.audiopreview.AudioPreviewPlayerActivity";

    private LibraryEntry() {
    }
}
