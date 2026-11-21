package dev.dlrevive.integrations;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Chooses the best audio-only stream, mirroring yt-dlp's {@code best*[vcodec=none]}
 * selection: highest bitrate, with a preference for the configured container.
 */
public final class AudioStreamSelector {

    private AudioStreamSelector() {
    }

    public static ResolvedStream select(List<AudioStream> streams, String preferredExtension) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }
        String preferred = preferredExtension == null
                ? "m4a" : preferredExtension.toLowerCase(Locale.ROOT);

        AudioStream best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AudioStream stream : streams) {
            if (stream == null || stream.getUrl() == null || stream.getUrl().isEmpty()) {
                continue;
            }
            String extension = extensionOf(stream);
            int score = bitrateOf(stream) + formatBonus(extension, preferred);
            if (score > bestScore) {
                bestScore = score;
                best = stream;
            }
        }
        if (best == null) {
            return null;
        }
        return toResolved(best);
    }

    private static int bitrateOf(AudioStream stream) {
        int bitrate = stream.getAverageBitrate();
        return bitrate > 0 ? bitrate : stream.getBitrate();
    }

    /** Small bonuses so a preferred/opus/m4a stream wins a close bitrate race. */
    private static int formatBonus(String extension, String preferred) {
        int bonus = 0;
        if (extension.equals(preferred)) {
            bonus += 50_000;
        }
        if (extension.equals("opus") || extension.equals("webm")) {
            bonus += 20_000;
        } else if (extension.equals("m4a")) {
            bonus += 10_000;
        }
        return bonus;
    }

    private static String extensionOf(AudioStream stream) {
        String suffix = null;
        MediaFormat format = stream.getFormat();
        if (format != null) {
            suffix = format.getSuffix();
        }
        if (suffix != null && !suffix.isEmpty()) {
            return suffix.toLowerCase(Locale.ROOT);
        }
        String codec = stream.getCodec();
        if (codec != null && codec.toLowerCase(Locale.ROOT).contains("opus")) {
            return "opus";
        }
        return "m4a";
    }

    private static ResolvedStream toResolved(AudioStream stream) {
        String codec = stream.getCodec() == null ? extensionOf(stream) : stream.getCodec();
        return new ResolvedStream(
                stream.getUrl(),
                extensionOf(stream),
                codec,
                bitrateOf(stream),
                Collections.singletonMap("Referer", "https://www.youtube.com/")
        );
    }
}
