package dev.dlrevive.integrations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A resolved, directly downloadable audio stream. */
public final class ResolvedStream {

    private final String url;
    private final String extension;
    private final String codec;
    private final int bitrate;
    private final Map<String, String> headers;

    public ResolvedStream(String url, String extension, String codec, int bitrate,
                          Map<String, String> headers) {
        this.url = url;
        this.extension = extension;
        this.codec = codec;
        this.bitrate = bitrate;
        this.headers = headers == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    public String getUrl() {
        return url;
    }

    public String getExtension() {
        return extension;
    }

    public String getCodec() {
        return codec;
    }

    public int getBitrate() {
        return bitrate;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
