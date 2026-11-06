# TubeMusic — traspaso técnico para el próximo agente

> Documento de continuidad. Leerlo antes de tocar código. La rama de trabajo es `redesign/no-vps`; no trabajar directamente sobre `main`.

## 1. Contexto del proyecto

TubeMusic es una aplicación Android nativa escrita en Java. Su objetivo de producto es ofrecer una interfaz sencilla para que una persona pegue un enlace de audio compatible y guarde el archivo autorizado en la carpeta Música del dispositivo.

El proyecto pasó por una limpieza y un rediseño:
- Se dejó una interfaz oscura con acentos violetas.
- Se separó la interfaz del proveedor que resuelve el contenido.
- Se eliminó la dependencia anterior de NewPipe y se quitó AdMob de esta etapa.
- Se evitó depender de un VPS para la interfaz y el flujo Android.
- Se conservó Android `DownloadManager` para delegar la transferencia del archivo ya resuelto al sistema.

**Estado funcional real:** la aplicación abre y muestra la interfaz, pero todavía NO tiene un extractor/proveedor funcional. `PendingAudioSourceResolver` devuelve un mensaje de proveedor pendiente. Por tanto, pegar un enlace de YouTube u otra plataforma no produce actualmente un archivo descargable. No presentar el APK instalado como prueba de que la extracción funciona.

## 2. Reglas de trabajo

1. Trabajar en `redesign/no-vps`. No modificar `main` ni fusionar nada sin autorización explícita.
2. Hacer cambios pequeños, verificables y en commits independientes. Evitar un commit gigante.
3. Antes de editar, comprobar el HEAD de la rama y revisar el contenido actual de los archivos; este documento puede quedar desactualizado.
4. Después de cada cambio, compilar y revisar el resultado de GitHub Actions. No afirmar que una función funciona hasta probarla.
5. Preservar el diseño, el logo y el flujo Android existentes salvo que haya una razón técnica concreta.
6. No reintroducir VPS, HTTP sin cifrar, NewPipe, AdMob ni dependencias sin justificar y validar.
7. No incluir secretos, claves privadas, tokens de GitHub ni credenciales en el repositorio.

## 3. Mapa del repositorio

### Raíz y configuración de compilación

- `settings.gradle`: nombre del proyecto (`TubeMusic`), módulo `:app` y repositorios Google/Maven Central.
- `build.gradle`: configuración raíz y Android Gradle Plugin 8.2.2.
- `gradle.properties`: opciones de Gradle, AndroidX y configuración de recursos.
- `app/build.gradle`: módulo Android. Namespace y applicationId `com.tubevideos`, compileSdk 34, minSdk 24, targetSdk 34, versionCode 2, versionName 1.1 y Java 17. Dependencias actuales: AppCompat 1.6.1, Material 1.11.0 y ConstraintLayout 2.1.4.
- `.github/workflows/android.yml`: CI de GitHub Actions. Usa Java 17 y Gradle 8.5, ejecuta `./gradlew --no-daemon :app:assembleDebug` y publica el artefacto `TubeMusic-debug`.

### Código Android — `app/src/main/java/com/tubevideos/`

- `MainActivity.java`: pantalla principal y coordinador del flujo. Lee el enlace, comprueba que no esté vacío, solicita permiso de almacenamiento en Android antiguo, llama al resolver y entrega el resultado a `enqueueDownload()`. Esta última valida que la URL final sea HTTPS, limpia el nombre del archivo y crea una solicitud de Android `DownloadManager` con destino `Music/`. También actualiza el estado y la barra de progreso.
  - Punto de sustitución actual: en `onCreate()` instancia `new PendingAudioSourceResolver()`.
  - Observación técnica: declara un `ExecutorService` y lo cierra en `onDestroy()`, pero hoy no se utiliza. El contrato exige que la implementación del resolver no bloquee el hilo principal. El próximo agente debe definir claramente quién administra el ejecutor, evitar fugas y evitar callbacks a una Activity destruida.
- `AudioSourceResolver.java`: contrato de integración. Método `resolve(String inputUrl, Callback callback)`; el callback informa `onResolved(ResolvedAudio)` o `onError(String)`. Mantener la lógica del proveedor detrás de este contrato.
- `ResolvedAudio.java`: objeto de datos que transporta `downloadUrl`, `title`, `mimeType` y `fileExtension`. No debe contener lógica de interfaz ni de extracción.
- `PendingAudioSourceResolver.java`: implementación temporal. Siempre informa que el proveedor todavía no está integrado. Debe reemplazarse solo cuando exista una implementación probada.

### Interfaz y recursos — `app/src/main/res/`

- `layout/activity_main.xml`: pantalla principal. Contiene logo, título, subtítulo, tarjeta, campo `etMediaUrl`, botón `btnDownload`, `progressBar` y `tvStatus`. Sus IDs son utilizados por `MainActivity`.
- `drawable/ic_logo_purple.xml`: logo vectorial aprobado como identidad visual de TubeMusic. Es el recurso utilizado por el manifest y la pantalla principal.
- `drawable/splash_screen.xml`: composición del fondo y logo para el fondo de ventana/splash.
- `drawable/bg_screen.xml`: fondo degradado de la pantalla.
- `drawable/ic_download.xml`: icono del botón de preparación/descarga.
- `values/colors.xml`: paleta negro/violeta, superficies, texto y colores secundarios.
- `values/strings.xml`: textos de interfaz, estados y errores. Mantener textos centralizados aquí.
- `values/styles.xml`: tema `Theme.TubeMusic`, derivado de Material Components sin ActionBar.

### Manifest y documentación

- `app/src/main/AndroidManifest.xml`: permisos INTERNET y ACCESS_NETWORK_STATE; permiso WRITE_EXTERNAL_STORAGE limitado a API 28; icono, tema y Activity launcher. `usesCleartextTraffic=false`: mantener HTTPS.
- `README.md`: descripción corta y enlace a este documento.
- `docs/AGENT_HANDOFF.md`: este traspaso.
- `docs/index.html`: página informativa del proyecto; no es la aplicación Android ni el proveedor.

## 4. Flujo de datos actual

```text
Usuario pega enlace
       |
       v
MainActivity.beginResolve()
       |
       v
AudioSourceResolver.resolve(inputUrl, callback)
       |
       +---- PendingAudioSourceResolver (actual)
       |          |
       |          +--> onError("proveedor pendiente")
       |
       +---- Implementación futura (todavía no existe)
                  |
                  +--> onResolved(ResolvedAudio)
                              |
                              v
                    MainActivity.enqueueDownload()
                              |
                              v
                    Android DownloadManager
                              |
                              v
                    Carpeta Música del dispositivo
```

El resolver debe entregar un archivo/URL directa de descarga, no una página HTML ni una URL de reproducción web. La validación HTTPS que hace `MainActivity` es una comprobación de transporte, no una comprobación de derechos o autorización.

## 5. Primera etapa del próximo agente: evaluar y configurar el proveedor

**No empezar escribiendo un extractor a ciegas.** Primero determinar qué fuentes se van a admitir y qué mecanismos oficiales/permitidos ofrece cada una.

1. Definir el alcance inicial con fuentes de audio que el usuario tenga derecho a descargar: archivos propios, contenido con licencia adecuada, almacenamiento propio o APIs que documenten expresamente la descarga/exportación.
2. Si se propone YouTube u otra plataforma de terceros, revisar sus términos y documentación oficial antes de implementar. No desarrollar mecanismos que extraigan streams protegidos, eludan DRM, restricciones técnicas, controles de acceso o condiciones del servicio. No asumir que una API de metadatos autoriza descargar el audio. Si no existe un método oficial y permitido para obtener el archivo, informar el bloqueo y proponer una fuente compatible; no disfrazar una extracción no autorizada como integración.
3. Redactar una nota breve de decisión: proveedor elegido, documentación oficial, permisos/alcance, formatos soportados, límites, datos que devuelve y motivo por el que la descarga está autorizada.
4. Implementar el proveedor en una clase nueva que implemente `AudioSourceResolver`; no meter la lógica en `MainActivity`.
5. Hacer las solicitudes de red fuera del hilo principal. Definir timeouts, cancelación, manejo de errores HTTP/red, validación de tipo MIME, extensión y URL HTTPS. No registrar enlaces privados ni datos sensibles en logs.
6. Devolver `ResolvedAudio` solo cuando exista un recurso descargable válido y autorizado. Si la fuente entrega un archivo temporal, comprobar su vigencia durante el tiempo que tarde en iniciar la descarga.
7. Reemplazar la instancia de `PendingAudioSourceResolver` en `MainActivity` por la implementación real, conservando el contrato y el resto del flujo.
8. Revisar el ciclo de vida: callbacks al hilo principal, evitar callbacks después de `onDestroy()`, liberar recursos y corregir el `ExecutorService` actualmente sin uso.
9. Agregar pruebas unitarias para URL vacía/malformada, URL final no HTTPS, respuesta vacía, formato no admitido, errores de red y callback de éxito/error. Añadir pruebas instrumentadas si son necesarias para el flujo de Android `DownloadManager`.
10. Compilar con el workflow existente y probar en emulador/dispositivo. Verificar que el archivo aparece en Música, que tiene nombre/extensión/MIME coherentes y que los errores no se presentan como descargas exitosas.

### Criterios de aceptación del proveedor

- No bloquea el hilo UI.
- No intenta descargar páginas HTML ni URLs de reproducción.
- Solo devuelve recursos HTTPS autorizados y realmente descargables.
- Los errores son claros y no dejan el botón bloqueado.
- La app no muestra éxito antes de que Android acepte la solicitud de descarga.
- No se han añadido dependencias innecesarias ni secretos.
- Build de Actions exitoso y prueba real documentada.

## 6. Decisiones de producto que todavía NO están implementadas

- **Tokens:** la idea de producto es que 1 token represente 1 audio, sin importar si dura 3 minutos o más. Se habló de cobrar solo cuando una operación autorizada termine correctamente; no descontar por pegar un enlace ni por un fallo. No existe todavía wallet, saldo persistente, ledger ni backend.
- **Recompensas:** el usuario eligió explorar anuncios recompensados y una tarjeta tipo flyer que se cierra con una X. Esto fue una propuesta de diseño, no una integración. Cerrar un anuncio por sí solo no demuestra que se haya ganado una recompensa; cualquier crédito futuro debe depender de una señal válida del proveedor de anuncios y sus políticas.
- **Monetización:** AdMob fue retirado y no está integrado. No agregar anuncios ni pagos como parte de la configuración inicial del proveedor.
- **Backend/VPS:** no hay backend de tokens ni VPS configurado. No inventar un saldo real usando solo preferencias locales.

Estas decisiones deben quedar fuera del primer hito del proveedor para mantener el alcance controlado.

## 7. Estado de compilación y referencia

El commit de base documentado es `c08778e75fa1f59bcdaede5bd0d05601758c5e24` (`Prepare TubeMusic UI and provider integration boundary`). La rama de trabajo es `redesign/no-vps`. Verificar el HEAD real antes de continuar, ya que puede haber commits posteriores.

El workflow `.github/workflows/android.yml` compila un APK debug y sube el artefacto `TubeMusic-debug`. Un build exitoso demuestra que el APK compila; no demuestra que el proveedor resuelva enlaces ni que el contenido esté autorizado.

## 8. Instrucción de cierre para el agente

Antes de modificar código, devolver un diagnóstico corto con:
- proveedor/fuente que se propone integrar y su documentación oficial;
- compatibilidad legal/técnica confirmada o bloqueo encontrado;
- archivos que piensa tocar;
- plan dividido en commits pequeños;
- pruebas que ejecutará.

Después, implementar solo el primer paso seguro y verificable. No tocar `main`.
