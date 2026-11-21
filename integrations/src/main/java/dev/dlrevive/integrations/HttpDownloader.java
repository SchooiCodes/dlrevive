package dev.dlrevive.integrations;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal {@link Downloader} for NewPipeExtractor backed by {@link HttpURLConnection}.
 *
 * <p>This keeps the integrations DEX small: no OkHttp dependency is needed. NewPipeExtractor
 * only needs GET/POST with headers and a UTF-8 body for its Innertube calls.</p>
 */
public final class HttpDownloader extends Downloader {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(request.url()).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod(request.httpMethod());
        connection.setRequestProperty("User-Agent", USER_AGENT);

        if (request.headers() != null) {
            for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
                for (String value : entry.getValue()) {
                    connection.addRequestProperty(entry.getKey(), value);
                }
            }
        }

        byte[] body = request.dataToSend();
        if (body != null && body.length > 0) {
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
        }

        int code = connection.getResponseCode();
        String message = connection.getResponseMessage();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() != null) {
                headers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        String responseBody = readBody(connection, code);
        String finalUrl = connection.getURL().toString();
        connection.disconnect();

        if (code == 429 || (responseBody != null
                && responseBody.toLowerCase(java.util.Locale.ROOT).contains("recaptcha"))) {
            throw new ReCaptchaException("YouTube requested a CAPTCHA", request.url());
        }
        return new Response(code, message, headers, responseBody, finalUrl);
    }

    private static String readBody(HttpURLConnection connection, int code) throws IOException {
        InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
