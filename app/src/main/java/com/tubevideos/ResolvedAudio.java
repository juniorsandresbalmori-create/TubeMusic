package com.tubevideos;

/** A resolved, downloadable audio resource returned by AudioSourceResolver. */
public final class ResolvedAudio {
    private final String downloadUrl;
    private final String title;
    private final String mimeType;
    private final String fileExtension;

    public ResolvedAudio(String downloadUrl, String title, String mimeType, String fileExtension) {
        this.downloadUrl = downloadUrl;
        this.title = title;
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
    }

    public String getDownloadUrl() { return downloadUrl; }
    public String getTitle() { return title; }
    public String getMimeType() { return mimeType; }
    public String getFileExtension() { return fileExtension; }
}
