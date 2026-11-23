package dev.dlrevive.integrations;

import android.content.Context;

import java.io.File;

/** Single source of truth for the dlrevive config file location and load-or-create logic. */
public final class ConfigStore {

    public static final String RELATIVE_PATH = "dlrevive/config.json";

    private ConfigStore() {
    }

    /** Canonical config file for the given app context. */
    public static File file(Context app) {
        return new File(app.getExternalFilesDir(null), RELATIVE_PATH);
    }

    /**
     * Load the config, materializing a defaults file on first run so the settings UI and the
     * downloader always observe the same persisted state.
     */
    public static DownloadConfig loadOrCreate(Context app) {
        File file = file(app);
        DownloadConfig config = DownloadConfig.load(file);
        if (!file.isFile()) {
            try {
                config.save(file);
            } catch (Exception ignored) {
                // Best-effort: defaults still apply in memory if the file cannot be written.
            }
        }
        return config;
    }
}
