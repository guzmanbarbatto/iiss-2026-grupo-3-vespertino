package com.ioteste.engine.controller;

import com.ioteste.engine.domain.ComandoControl;
import com.ioteste.engine.domain.EstadoControl;
import com.ioteste.engine.service.EvaluadorJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/control")
public class ControlController {

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    // Variables de estado en memoria
    private String estado = "DETENIDO";
    private String fechaHora = null;
    private Double factorTiempo = null;
    private String iniciadoEn = null;

    // Dependencia del motor para propagar el factor de aceleración
    private final EvaluadorJobService evaluadorJobService;

    public ControlController(EvaluadorJobService evaluadorJobService) {
        this.evaluadorJobService = evaluadorJobService;
    }

    @GetMapping
    public ResponseEntity<EstadoControl> obtenerControl() {
        if ("EN_EJECUCION".equals(this.estado)) {
            String ahoraLogico = evaluadorJobService.obtenerRelojSimulado()
                    .toInstant()
                    .atOffset(ZoneOffset.UTC)
                    .format(ISO_UTC);
            return ResponseEntity.ok(new EstadoControl(estado, ahoraLogico, factorTiempo, iniciadoEn));
        }
        return ResponseEntity.ok(new EstadoControl(estado, fechaHora, factorTiempo, iniciadoEn));
    }

    @PostMapping("/comandos")
    public ResponseEntity<EstadoControl> comandarControl(@RequestBody ComandoControl comando) {
        if (comando == null || comando.accion() == null) {
            return ResponseEntity.badRequest().build();
        }

        if ("INICIAR".equals(comando.accion())) {
            // El contrato exige un 400 si se envía INICIAR sin una fechaHora o si no es ISO-8601
            if (comando.fechaHora() == null || comando.fechaHora().isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            try {
                ZonedDateTime.parse(comando.fechaHora());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().build();
            }

            // El contrato exige rechazar con 400 si factorTiempo no es mayor que cero
            if (comando.factorTiempo() != null
                    && (comando.factorTiempo() <= 0 || comando.factorTiempo().isNaN()
                            || comando.factorTiempo().isInfinite())) {
                return ResponseEntity.badRequest().build();
            }

            this.estado = "EN_EJECUCION";
            this.fechaHora = comando.fechaHora();
            this.factorTiempo = (comando.factorTiempo() != null) ? comando.factorTiempo() : 1.0;

            // Genera la hora real del servidor en formato ISO-8601 (UTC)
            this.iniciadoEn = Instant.now().atOffset(ZoneOffset.UTC).format(ISO_UTC);

            // Notificar al motor para que arranque con el factor de aceleración
            evaluadorJobService.iniciarMotor(this.factorTiempo, this.fechaHora);

        } else if ("DETENER".equals(comando.accion())) {
            this.estado = "DETENIDO";
            this.fechaHora = null;
            this.factorTiempo = null;
            this.iniciadoEn = null;

            // Notificar al motor para que se detenga
            evaluadorJobService.detenerMotor();
        } else {
            // Rechaza si la acción no es válida
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(new EstadoControl(estado, fechaHora, factorTiempo, iniciadoEn));
    }
}
