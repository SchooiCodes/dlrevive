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

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dlrevive-download");
        thread.setDaemon(true);
        return thread;
    });

    private DownloadIntegration() {
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

    /** Resolver selection. The authenticated resolver is added in a later milestone. */
    private static StreamResolver createResolver(DownloadConfig config) {
        if (config.getAuthMode() == DownloadConfig.AuthMode.AUTHENTICATED) {
            Log.w(TAG, "Authenticated mode is not available in this build; using anonymous");
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
