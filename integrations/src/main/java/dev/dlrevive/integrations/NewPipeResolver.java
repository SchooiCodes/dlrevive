package dev.dlrevive.integrations;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

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
        // Build the extractor directly from the known video ID instead of calling
        // StreamInfo.getInfo(url). The URL-parsing path calls NewPipeExtractor's
        // Utils.decodeUrlUtf8 -> URLDecoder.decode(String, Charset), which was added in API 30
        // and throws NoSuchMethodError on API 29 devices. Passing the id directly via a
        // LinkHandler bypasses that parsing entirely.
        try {
            StreamingService service = NewPipe.getService(0); // YouTube
            String url = "https://www.youtube.com/watch?v=" + videoId;
            LinkHandler link = new LinkHandler(url, url, videoId);
            StreamExtractor extractor = service.getStreamExtractor(link);
            extractor.fetchPage();
            // Read audio streams straight from the extractor. StreamInfo.getInfo(extractor)
            // also parses thumbnails, which calls Collectors.toUnmodifiableList() (API 30+)
            // and throws NoSuchMethodError on API 29. We only need the audio stream URLs.
            java.util.List<AudioStream> audioStreams = extractor.getAudioStreams();

            ResolvedStream stream = AudioStreamSelector.select(
                    audioStreams, preferredExtension);
            if (stream == null) {
                throw new ResolutionException("No audio-only stream available for " + videoId);
            }
            return stream;
        } catch (ResolutionException e) {
            throw e;
        } catch (Throwable t) {
            throw new ResolutionException("Could not extract " + videoId, t);
        }
    }

    private static synchronized void init() {
        if (!initialized) {
            NewPipe.init(new HttpDownloader(), Localization.DEFAULT);
            initialized = true;
        }
    }
}
