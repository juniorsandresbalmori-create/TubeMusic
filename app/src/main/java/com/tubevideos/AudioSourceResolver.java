package com.tubevideos;

/**
 * Integration boundary for the future content provider.
 *
 * Implementations resolve a user-supplied URL into a direct HTTPS media file
 * that the user is authorized to download. This interface intentionally
 * contains no platform-specific extraction logic.
 */
public interface AudioSourceResolver {
    void resolve(String inputUrl, Callback callback);

    interface Callback {
        void onResolved(ResolvedAudio audio);
        void onError(String message);
    }
}
