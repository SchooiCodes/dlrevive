package dev.dlrevive.integrations;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;

/**
 * Anonymous resolver backed by NewPipeExtractor. This is the in-process equivalent of
 * {@code yt-dlp -f bestaudio} against a YouTube Music watch URL.
 */
public final class NewPipeResolver implements StreamResolver {

    private static boolean initialized;

    private final String preferredExtension;

    public NewPipeResolver(String preferredExtension) {
        this.preferredExtension = preferredExtension;
    }

    @Override
    public ResolvedStream resolve(String videoId) throws ResolutionException {
        init();
        StreamInfo info = null;
        ResolutionException lastFailure = null;
        for (String host : new String[]{"music.youtube.com", "www.youtube.com"}) {
            String url = "https://" + host + "/watch?v=" + videoId;
            try {
                info = StreamInfo.getInfo(url);
                lastFailure = null;
                break;
            } catch (Exception e) {
                lastFailure = new ResolutionException("Could not extract " + url, e);
            }
        }
        if (info == null) {
            throw lastFailure == null
                    ? new ResolutionException("No extractor available for " + videoId)
                    : lastFailure;
        }

        ResolvedStream stream = AudioStreamSelector.select(
                info.getAudioStreams(), preferredExtension);
        if (stream == null) {
            throw new ResolutionException("No audio-only stream available for " + videoId);
        }
        return stream;
    }

    private static synchronized void init() {
        if (!initialized) {
            NewPipe.init(new HttpDownloader(), Localization.DEFAULT);
            initialized = true;
        }
    }
}
