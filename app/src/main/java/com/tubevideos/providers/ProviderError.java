package com.tubevideos.providers;

/** Common error categories returned by audio providers. */
public enum ProviderError {
    INVALID_URL("La dirección proporcionada no es válida."),
    NETWORK("No se pudo establecer una conexión."),
    UNAVAILABLE("El contenido solicitado no está disponible."),
    NOT_AUTHORIZED("La fuente no autoriza esta operación."),
    UNSUPPORTED_FORMAT("El formato de audio no es compatible."),
    EMPTY_RESPONSE("El proveedor devolvió una respuesta vacía."),
    UNKNOWN("Ocurrió un error inesperado.");

    private final String defaultMessage;

    ProviderError(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
