mkdir -p docs && cat > docs/PROVIDER_DECISION.md <<'EOF'
# Decisión: resolución de fuentes con yt-dlp

## Qué se integra

yt-dlp embebido con Chaquopy 15.0.1 sobre Python 3.11, ejecutado en el dispositivo.
No hay backend ni VPS.

## Licencia

yt-dlp es Unlicense (dominio público). Chaquopy es de uso libre.

## Por qué

- Es el extractor con mantenimiento más activo del ecosistema.
- Encaja con `AudioSourceResolver`: devuelve URL directa y metadatos sin acoplarse a la interfaz.
- No requiere ffmpeg si se pide `bestaudio` y se conserva el contenedor nativo.

## Qué devuelve

`ResolvedAudio` con URL HTTPS directa, título, MIME, extensión y cabeceras HTTP
(incluida `User-Agent`), que `MainActivity` aplica con `addRequestHeader`.

## Límites aceptados

- Sin ffmpeg: no hay conversión a MP3, solo `m4a`/`webm` nativos.
- APK +15-25 MB y arranque de Python de 2-4 s en frío.
- La versión de yt-dlp queda fija en el APK. Cuando el extractor se rompa, hará
  falta publicar actualización: es el precio de no tener VPS.
- YouTube puede responder con verificación anti-bot en redes móviles.
- yt-dlp hace extracción, no descarga autorizada por contrato. La app no debe
  presentarlo como descarga oficial de la plataforma.
EOF
