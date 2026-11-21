package dev.dlrevive.integrations;

/** Placeholder resolver used until the real resolvers are implemented. */
public final class UnavailableResolver implements StreamResolver {

    @Override
    public ResolvedStream resolve(String videoId) throws ResolutionException {
        throw new ResolutionException(
                "No stream resolver is configured yet for " + videoId
                        + " (integrations build is incomplete)");
    }
}
