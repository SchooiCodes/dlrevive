package dev.dlrevive.integrations;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point called from the injected YouTube Music menu action.
 *
 * <p>All public methods must be safe to call from the app's main thread: resolution and
 * enqueueing happen on a background executor and user feedback is posted back to the main
 * looper.</p>
 */
public final class DownloadIntegration {

    public static final String TAG = "dlrevive";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:v=|/v/|/embed/|/live/|youtu\\.be/)([A-Za-z0-9_-]{11})");

    /** Matches the bare 11-character video id passed to YTM's native offline action handler. */
    private static final Pattern VIDEO_ID_ONLY = Pattern.compile("[A-Za-z0-9_-]{11}");

    private static volatile Context appContext;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dlrevive-download");
        thread.setDaemon(true);
        return thread;
    });

    private DownloadIntegration() {
    }

    /**
     * Captures the Application Context once at startup. Called from the first Application.onCreate
     * in the patched app; keeping it here avoids needing a new Android component/manifest entry.
     */
    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
            Log.i(TAG, "Native download hook installed");
            Toast.makeText(appContext, "dlrevive patch active", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handles the native YTM Download action. The patched hook calls this before the original
     * Premium-gated offline command. Returns true when the action was handled (so the patch can
     * return from the original method), false for non-video commands that must be left alone.
     */
    public static boolean handleNativeDownload(String videoId) {
        if (appContext == null || videoId == null || !VIDEO_ID_ONLY.matcher(videoId).matches()) {
            return false;
        }
        Log.i(TAG, "Native download tapped: " + videoId);
        Toast.makeText(appContext, "dlrevive: Download tapped " + videoId, Toast.LENGTH_SHORT).show();
        download(appContext, videoId, null);
        return true;
    }

    /** Start a download for the given song. Safe to call from any thread. */
    public static void download(Context context, String videoId, String title) {
        if (context == null || videoId == null || videoId.trim().isEmpty()) {
            Log.e(TAG, "download() called with no context or video id");
            return;
        }
        final Context app = context.getApplicationContext();
        final String id = videoId.trim();
        final String name = title == null || title.trim().isEmpty() ? id : title.trim();

        EXECUTOR.execute(() -> {
            try {
                DownloadConfig config = loadConfig(app);
                StreamResolver resolver = createResolver(config);
                ResolvedStream stream = resolver.resolve(id);
                long requestId = enqueue(app, config, stream, name);
                Log.i(TAG, "Queued download " + requestId + " for " + name);
                toast(app, "Downloading: " + name);
            } catch (Exception e) {
                Log.e(TAG, "Download failed for " + id, e);
                toast(app, "Download failed: " + e.getMessage());
            }
        });
    }

    /**
     * Hook used with the YTM share action: starts a download from the URL being shared. The video
     * ID is parsed from the shared watch or short link, and the shared title is reused for the
     * filename. Sharing a song therefore also downloads it.
     */
    public static void downloadFromUri(Context context, Uri uri, String title) {
        if (uri == null) {
            Log.e(TAG, "downloadFromUri() called with no uri");
            return;
        }
        String id = extractVideoId(uri.toString());
        if (id == null) {
            Log.w(TAG, "No video id in shared uri: " + uri);
            return;
        }
        download(context, id, title);
    }

    /** Extract an 11-character YouTube video ID from a watch, short, live, or embed URL. */
    static String extractVideoId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static DownloadConfig loadConfig(Context app) {
        File file = new File(app.getExternalFilesDir(null), "dlrevive/config.json");
        DownloadConfig config = DownloadConfig.load(file);
        if (!file.isFile()) {
            try {
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (Writer writer = new OutputStreamWriter(
                        new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    writer.write(config.toJson());
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not write default config", e);
            }
        }
        return config;
    }

    /** Resolver selection, driven by the persisted config. */
    private static StreamResolver createResolver(DownloadConfig config) {
        if (config.getAuthMode() == DownloadConfig.AuthMode.AUTHENTICATED) {
            return new InnertubeResolver(config);
        }
        return new NewPipeResolver(config.getPreferredExtension());
    }

    private static long enqueue(Context app, DownloadConfig config, ResolvedStream stream,
                                String title) {
        String extension = stream.getExtension().replaceAll("[^a-z0-9]", "");
        if (extension.isEmpty()) {
            extension = "m4a";
        }
        String relativePath = config.getDownloadSubdirectory() + "/"
                + FileNames.sanitize(title) + "." + extension;

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(stream.getUrl()));
        request.setTitle(title);
        request.setDescription(extension.toUpperCase(Locale.ROOT) + " audio");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverRoaming(false);
        request.setAllowedOverMetered(config.isAllowMobileData());
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, relativePath);
        request.addRequestHeader("User-Agent", USER_AGENT);
        for (Map.Entry<String, String> header : stream.getHeaders().entrySet()) {
            request.addRequestHeader(header.getKey(), header.getValue());
        }

        DownloadManager manager =
                (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("DownloadManager is unavailable");
        }
        return manager.enqueue(request);
    }

    private static void toast(Context app, String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(app, message, Toast.LENGTH_LONG).show());
    }
}
