package dev.dlrevive.integrations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;

/**
 * Standalone, non-stubbed check of the real resolver and download path used by the patch.
 *
 * <p>Run with a Gradle JavaExec task using the test runtime classpath. Takes a real video id,
 * resolves a real audio stream through NewPipeExtractor, then fetches the first 1 MiB of it
 * over HTTP and writes it to disk. No Android APIs are involved.</p>
 */
public final class LiveResolverHarness {

    private LiveResolverHarness() {
    }

    public static void main(String[] args) throws Exception {
        String videoId = args.length > 0 ? args[0] : "vQqLdfJQPKw";
        File output = new File(args.length > 1 ? args[1] : "build/live-harness-sample.m4a");
        output.getParentFile().mkdirs();

        DownloadConfig config = DownloadConfig.defaults();
        StreamResolver resolver = DownloadPipeline.selectResolver(
                config, new NewPipeResolver(config.getPreferredExtension()), null);
        System.out.println("videoId=" + videoId);
        System.out.println("resolver=" + resolver.getClass().getSimpleName());

        long start = System.currentTimeMillis();
        ResolvedStream stream = resolver.resolve(videoId);
        System.out.println("resolvedInMs=" + (System.currentTimeMillis() - start));
        System.out.println("url=" + redact(stream.getUrl()));
        System.out.println("host=" + new URL(stream.getUrl()).getHost());
        System.out.println("extension=" + stream.getExtension());
        System.out.println("codec=" + stream.getCodec());
        System.out.println("bitrate=" + stream.getBitrate());
        System.out.println("headers=" + stream.getHeaders());

        DownloadPipeline.Request request = DownloadPipeline.buildRequest(config, stream, "harness");
        System.out.println("relativePath=" + request.relativePath);
        System.out.println("description=" + request.description);

        HttpURLConnection connection = (HttpURLConnection) new URL(stream.getUrl()).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        connection.setRequestProperty("Range", "bytes=0-1048575");
        int status = connection.getResponseCode();
        System.out.println("httpStatus=" + status);
        System.out.println("contentType=" + connection.getHeaderField("Content-Type"));
        System.out.println("contentLength=" + connection.getHeaderField("Content-Length"));
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("stream GET failed with HTTP " + status);
        }

        long total = 0;
        byte[] buffer = new byte[16 * 1024];
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(output)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
            }
        } finally {
            connection.disconnect();
        }
        System.out.println("file=" + output.getAbsolutePath());
        System.out.println("bytes=" + total);
        System.out.println("sizeMiB=" + String.format(Locale.ROOT, "%.2f", total / 1048576.0));
        System.out.println("HARNESS_OK");
    }

    private static String redact(String url) {
        int query = url.indexOf('?');
        if (query < 0) {
            return url;
        }
        return url.substring(0, query) + "?<redacted>";
    }
}
