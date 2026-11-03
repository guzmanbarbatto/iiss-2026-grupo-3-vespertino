package com.ioteste.engine.controller;

import com.ioteste.engine.domain.ErrorRespuesta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce las excepciones a respuestas con el cuerpo de error uniforme del contrato. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> cuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.de(400, "el cuerpo no es un JSON válido para esta operación"));
    }

    /** Un dato que la base rechaza (por ejemplo, un texto más largo que la columna) es un error del cliente. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRespuesta> datoRechazado(DataIntegrityViolationException e) {
        String causa = e.getMostSpecificCause().getMessage();
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.de(400, "la base de datos rechazó el inventario: " + causa));
    }

    /** Cualquier otra falla de la base no es culpa del cliente: el servicio no está disponible. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorRespuesta> baseNoDisponible(DataAccessException e) {
        LOG.error("Error de base de datos: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorRespuesta.de(503, "la base de datos no está disponible"));
    }
}
