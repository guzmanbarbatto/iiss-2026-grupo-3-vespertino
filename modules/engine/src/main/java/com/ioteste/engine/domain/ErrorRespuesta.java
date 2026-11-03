package com.ioteste.engine.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Cuerpo de error uniforme del contrato: {@code {timestamp, status, mensaje, errores}}. */
public record ErrorRespuesta(
    String timestamp,
    int status,
    String mensaje,
    List<String> errores
) {

    /** Formato de fecha de todo el API: ISO-8601 en UTC con milisegundos. */
    public static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    public static ErrorRespuesta de(int status, String mensaje, List<String> errores) {
        return new ErrorRespuesta(ISO_UTC.format(Instant.now()), status, mensaje, errores);
    }

    public static ErrorRespuesta de(int status, String mensaje) {
        return de(status, mensaje, null);
    }
}
