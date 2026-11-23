package dev.dlrevive.integrations;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Persisted settings for the downloader.
 *
 * <p>The file lives at {@code <external files dir>/dlrevive/config.json}. It is created with
 * defaults on first use so users can edit it without a settings UI.
 */
public final class DownloadConfig {

    public enum AuthMode {
        ANONYMOUS,
        AUTHENTICATED
    }

    private static final Gson GSON = new Gson();

    @SerializedName("authMode")
    private AuthMode authMode = AuthMode.ANONYMOUS;

    @SerializedName("cookieHeader")
    private String cookieHeader = "";

    @SerializedName("poToken")
    private String poToken = "";

    @SerializedName("visitorData")
    private String visitorData = "";

    @SerializedName("preferredExtension")
    private String preferredExtension = "m4a";

    @SerializedName("downloadSubdirectory")
    private String downloadSubdirectory = "dlrevive";

    @SerializedName("allowMobileData")
    private boolean allowMobileData = true;

    /** Master switch for the download hooks; when false the hooks fall through to YTM. */
    @SerializedName("enabled")
    private boolean enabled = true;

    public static DownloadConfig defaults() {
        return new DownloadConfig();
    }

    /** Parse a config file, falling back to defaults when it is missing or malformed. */
    public static DownloadConfig load(File file) {
        if (file == null || !file.isFile()) {
            return defaults();
        }
        try (InputStreamReader reader =
                     new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            DownloadConfig parsed = GSON.fromJson(reader, DownloadConfig.class);
            return parsed == null ? defaults() : parsed.normalized();
        } catch (Exception e) {
            return defaults();
        }
    }

    /** Serialize current settings to JSON (used to materialize defaults on first run). */
    public String toJson() {
        return GSON.toJson(normalized());
    }

    /** Persist current settings to {@code file}, creating parent directories as needed. */
    public void save(File file) throws java.io.IOException {
        if (file == null) {
            throw new java.io.IOException("config file is null");
        }
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(toJson());
        }
    }

    private DownloadConfig normalized() {
        if (authMode == null) {
            authMode = AuthMode.ANONYMOUS;
        }
        if (cookieHeader == null) {
            cookieHeader = "";
        }
        if (poToken == null) {
            poToken = "";
        }
        if (visitorData == null) {
            visitorData = "";
        }
        if (preferredExtension == null || preferredExtension.trim().isEmpty()) {
            preferredExtension = "m4a";
        } else {
            preferredExtension = preferredExtension.trim().toLowerCase(Locale.ROOT);
        }
        if (downloadSubdirectory == null || downloadSubdirectory.trim().isEmpty()) {
            downloadSubdirectory = "dlrevive";
        }
        return this;
    }

    public AuthMode getAuthMode() {
        return authMode;
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    public String getPoToken() {
        return poToken;
    }

    public String getVisitorData() {
        return visitorData;
    }

    public String getPreferredExtension() {
        return preferredExtension;
    }

    public String getDownloadSubdirectory() {
        return FileNames.sanitize(downloadSubdirectory);
    }

    public boolean isAllowMobileData() {
        return allowMobileData;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPreferredExtension(String preferredExtension) {
        this.preferredExtension = preferredExtension;
        normalized();
    }

    public void setDownloadSubdirectory(String downloadSubdirectory) {
        this.downloadSubdirectory = downloadSubdirectory;
        normalized();
    }

    public void setAllowMobileData(boolean allowMobileData) {
        this.allowMobileData = allowMobileData;
    }
}
