# TubeMusic

Aplicación Android nativa en Java para preparar descargas de audio compatibles, con una interfaz oscura y acentos violetas.

La interfaz y la capa de descarga Android están separadas del proveedor de contenido. El proveedor está pendiente de integración; por ahora la app informa ese estado y no realiza resolución de fuentes.

Consulta [docs/AGENT_HANDOFF.md](docs/AGENT_HANDOFF.md) para el contrato técnico y el contexto del próximo agente.

GitHub Actions compila el APK debug con Java 17 y Gradle 8.5. No considerar el APK verificado hasta que el workflow finalice correctamente.
