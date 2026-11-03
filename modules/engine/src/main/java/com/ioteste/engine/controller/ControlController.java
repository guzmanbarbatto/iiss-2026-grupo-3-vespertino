package com.ioteste.engine.controller;

import com.ioteste.engine.domain.ComandoControl;
import com.ioteste.engine.domain.ErrorRespuesta;
import com.ioteste.engine.domain.EstadoControl;
import com.ioteste.engine.service.EvaluadorJobService;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** §5.2: arrancar y detener el control. El estado vive en el motor, no en este controlador. */
@RestController
@RequestMapping("/control")
public class ControlController {

    private final EvaluadorJobService motor;

    public ControlController(EvaluadorJobService motor) {
        this.motor = motor;
    }

    @GetMapping
    public EstadoControl obtenerControl() {
        return motor.estadoControl();
    }

    @PostMapping("/comandos")
    public ResponseEntity<?> comandarControl(@RequestBody ComandoControl comando) {
        if (comando.accion() == null) {
            return error("accion es obligatoria (INICIAR o DETENER)");
        }
        return switch (comando.accion()) {
            case "INICIAR" -> iniciar(comando);
            case "DETENER" -> ResponseEntity.ok(motor.detenerMotor());
            default -> error("accion desconocida '" + comando.accion() + "': debe ser INICIAR o DETENER");
        };
    }

    private ResponseEntity<?> iniciar(ComandoControl comando) {
        if (comando.fechaHora() == null || comando.fechaHora().isBlank()) {
            return error("INICIAR requiere fechaHora");
        }
        Instant fechaHora;
        try {
            fechaHora = ZonedDateTime.parse(comando.fechaHora()).toInstant();
        } catch (DateTimeParseException e) {
            return error("fechaHora no es un instante ISO-8601 válido: '" + comando.fechaHora() + "'");
        }
        double factor = comando.factorTiempo() == null ? 1.0 : comando.factorTiempo();
        if (!(factor > 0) || Double.isInfinite(factor)) {
            return error("factorTiempo debe ser un número mayor que cero (es " + comando.factorTiempo() + ")");
        }
        return ResponseEntity.ok(motor.iniciarMotor(fechaHora, factor));
    }

    private static ResponseEntity<ErrorRespuesta> error(String mensaje) {
        return ResponseEntity.badRequest().body(ErrorRespuesta.de(400, mensaje));
    }
}
