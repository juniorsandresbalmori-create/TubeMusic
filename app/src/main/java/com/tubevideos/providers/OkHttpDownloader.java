package com.tubevideos.providers;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Small HTTP client wrapper for providers that explicitly authorize
 * retrieving downloadable media files.
 *
 * The caller owns the returned Response and must close it.
 */
public final class OkHttpDownloader {

    private final OkHttpClient client;

    public OkHttpDownloader() {
        this(new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build());
    }

    public OkHttpDownloader(OkHttpClient client) {
        if (client == null) {
            throw new IllegalArgumentException("HTTP client must not be null.");
        }
        this.client = client;
    }

    public Response execute(Request request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null.");
        }
        return client.newCall(request).execute();
    }

    public OkHttpClient getClient() {
        return client;
    }
}
