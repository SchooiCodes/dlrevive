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

    static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:v=|/v/|/embed/|/live/|youtu\\.be/)([A-Za-z0-9_-]{11})");

    /** The native download command carries a bare video id as its token. */
    private static final Pattern BARE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{11}");

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dlrevive-download");
        thread.setDaemon(true);
        return thread;
    });

    private DownloadIntegration() {
    }

    /**
     * Primary trigger: the native per-song Download action. The patched hook calls this with the
     * Activity Context and the String token from the OfflineVideoCommand proto before YTM's own
     * (Premium-gated) track controller runs. We handle the download and return so the gate never
     * blocks it.
     */
    public static void handleTrackDownload(Context context, String token) {
        // The About screen reads this to tell "patch applied" from "patch ran".
        RuntimeState.markDownloadHookRan();
        Log.d(TAG, "download button hook fired: token=" + token);
        if (context == null) {
            Log.e(TAG, "dlrevive: failed — no context passed to the download button hook");
            return;
        }
        String id = extractTrackId(token);
        if (id == null) {
            Log.w(TAG, "dlrevive: failed — no video id in download command: " + token);
            toast(context.getApplicationContext(),
                    "dlrevive: failed — no video id in download command");
            return;
        }
        download(context, id, null);
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
        Log.d(TAG, "download requested: id=" + id + " title=" + name);

        EXECUTOR.execute(() -> {
            try {
                DownloadConfig config = loadConfig(app);
                NewPipeResolver newPipe = new NewPipeResolver(config.getPreferredExtension());
                InnertubeResolver innertube = new InnertubeResolver(config);
                StreamResolver primary = DownloadPipeline.selectResolver(
                        config, newPipe, innertube);
                StreamResolver fallback = DownloadPipeline.fallbackResolver(
                        config, newPipe, innertube);
                Log.d(TAG, "resolving stream for " + id
                        + " via " + primary.getClass().getSimpleName());
                ResolvedStream stream = DownloadPipeline.resolveWithFallback(primary, fallback, id);
                Log.d(TAG, "stream resolved for " + id + ": " + stream.getUrl());
                long requestId = enqueue(app, config, stream, name);
                Log.d(TAG, "DownloadManager enqueued request " + requestId
                        + " for " + name + " -> " + stream.getUrl());
                toast(app, "dlrevive: download started — " + name);
            } catch (Exception e) {
                String reason = DownloadPipeline.friendlyReason(e);
                Log.e(TAG, "Download failed for " + id + ": " + reason, e);
                toast(app, "dlrevive: failed — " + reason);
            }
        });
    }

    /**
     * Hook used with the YTM share action: starts a download from the URL being shared. The video
     * ID is parsed from the shared watch or short link, and the shared title is reused for the
     * filename. Sharing a song therefore also downloads it.
     */
    public static void downloadFromUri(Context context, Uri uri, String title) {
        // The About screen reads this to tell "patch applied" from "patch ran".
        RuntimeState.markDownloadHookRan();
        Log.d(TAG, "share hook fired: uri=" + uri + " title=" + title);
        if (context == null) {
            Log.e(TAG, "dlrevive: failed — no context passed to the download hook");
            return;
        }
        String id = uri == null ? null : extractVideoId(uri.toString());
        if (id == null) {
            Log.w(TAG, "dlrevive: failed — no video id in shared uri: " + uri);
            toast(context.getApplicationContext(),
                    "dlrevive: failed — no video id in shared link");
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

    /** The native command token is usually a bare id, but tolerate a full URL as well. */
    static String extractTrackId(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim();
        if (BARE_ID_PATTERN.matcher(value).matches()) {
            return value;
        }
        return extractVideoId(value);
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

    private static long enqueue(Context app, DownloadConfig config, ResolvedStream stream,
                                String title) {
        DownloadPipeline.Request spec = DownloadPipeline.buildRequest(config, stream, title);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(spec.url));
        request.setTitle(spec.title);
        request.setDescription(spec.description);
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverRoaming(false);
        request.setAllowedOverMetered(spec.allowMobileData);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, spec.relativePath);
        for (Map.Entry<String, String> header : spec.headers.entrySet()) {
            request.addRequestHeader(header.getKey(), header.getValue());
        }

        DownloadManager manager =
                (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("DownloadManager is unavailable");
        }
        return manager.enqueue(request);
    }

    /** Short, user-readable failure reason for the toast (exception messages can be very long). */
    static String shortReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 120 ? message.substring(0, 117) + "..." : message;
    }

    private static void toast(Context app, String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(app, message, Toast.LENGTH_LONG).show());
    }
}
