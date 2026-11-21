package dev.dlrevive.integrations;

/** Strategy for turning a video ID into a direct audio stream URL. */
public interface StreamResolver {

    /**
     * @param videoId the YouTube video ID (11 characters)
     * @return the best available audio-only stream
     * @throws ResolutionException when no playable stream can be found
     */
    ResolvedStream resolve(String videoId) throws ResolutionException;
}
