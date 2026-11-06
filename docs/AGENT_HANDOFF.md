# Contexto de integración para el próximo agente

## Estado del proyecto

- Aplicación Android nativa Java, paquete `com.tubevideos`.
- Interfaz rediseñada con identidad visual negro/violeta.
- La app no es un reproductor: prepara una descarga y delega el guardado a Android `DownloadManager`.
- No hay VPS configurado ni se necesita un backend VPS para la capa de interfaz.
- No se integra AdMob en esta etapa.
- La lógica de resolución/extracción de fuentes está deliberadamente pendiente.

## Punto de integración

- `app/src/main/java/com/tubevideos/AudioSourceResolver.java`: contrato del proveedor.
- `app/src/main/java/com/tubevideos/ResolvedAudio.java`: modelo del recurso final.
- `app/src/main/java/com/tubevideos/PendingAudioSourceResolver.java`: implementación temporal.
- `app/src/main/java/com/tubevideos/MainActivity.java`: interfaz y descarga del recurso ya resuelto.

La actividad llama `AudioSourceResolver.resolve(inputUrl, callback)`. La futura implementación devuelve un `ResolvedAudio` mediante `onResolved` o un mensaje mediante `onError`. El modelo contiene:

- `downloadUrl`: dirección HTTPS directa del archivo de audio final.
- `title`: nombre legible para notificación y archivo.
- `mimeType`: tipo MIME, si se conoce.
- `fileExtension`: extensión sin punto; si falta, la app usa `m4a`.

## Requisitos para integrar el proveedor

1. Implementar una clase separada que implemente `AudioSourceResolver`.
2. Mantener la resolución fuera del hilo principal.
3. Entregar únicamente recursos que el usuario esté autorizado a descargar y que el proveedor permita obtener.
4. Devolver una URL HTTPS válida y estable para iniciar la descarga; no devolver páginas HTML ni URLs de visualización.
5. Manejar errores de red, contenido no disponible, formatos no compatibles, restricciones del proveedor y cancelación con mensajes claros.
6. No mover lógica de resolución a la vista ni modificar innecesariamente el diseño, el contrato del modelo o el flujo de guardado.
7. Reemplazar `new PendingAudioSourceResolver()` en `MainActivity.onCreate()` por la implementación terminada.
8. No reintroducir configuración de VPS, tráfico HTTP sin cifrar ni dependencias no utilizadas.
9. Añadir pruebas para entradas, callbacks y errores. Verificar el flujo en un dispositivo/emulador y revisar el resultado real de DownloadManager.
10. Antes de integrar bibliotecas, comprobar coordenadas, versión, repositorio Maven y compatibilidad con Gradle/AGP.

## Límites actuales

- El botón informa que el proveedor está pendiente; todavía no realiza resolución de fuentes.
- La app valida HTTPS en el límite de descarga. Esto no sustituye las comprobaciones de autorización que debe hacer el proveedor.
- No afirmar que el APK está listo ni que la descarga completa funciona hasta que Actions termine correctamente y se pruebe en un dispositivo.

## Verificación

GitHub Actions compila con Java 17 y Gradle 8.5 mediante `./gradlew --no-daemon :app:assembleDebug` para pushes a `main` y `redesign/no-vps`, y para pull requests hacia `main`. El artefacto se publica como `TubeMusic-debug` si el build termina correctamente.
