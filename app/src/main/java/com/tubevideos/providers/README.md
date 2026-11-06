# Providers package

This package contains shared infrastructure for audio providers.

## Current files

- `OkHttpDownloader`: reusable OkHttp client wrapper. The caller must close each returned HTTP response.
- `ProviderError`: normalized error categories for provider implementations.

## Integration boundary

The app currently uses `PendingAudioSourceResolver`. Keep it active until a concrete provider is selected, its download permissions are documented, and its implementation is tested.

Do not use a metadata/search API as proof that downloading is permitted. YouTube catalog search and playback must remain separate from direct-file download resolution. A future resolver must only return a direct HTTPS media file when the source explicitly authorizes downloading it.

No NewPipe extractor, stream extraction, API keys, or credentials belong in this package at this stage.
