package com.tubevideos;

/**
 * Temporary implementation so the Android app remains buildable while the
 * provider integration is developed separately.
 */
public final class PendingAudioSourceResolver implements AudioSourceResolver {
    @Override
    public void resolve(String inputUrl, Callback callback) {
        callback.onError("El proveedor de audio todavía no está integrado.");
    }
}
