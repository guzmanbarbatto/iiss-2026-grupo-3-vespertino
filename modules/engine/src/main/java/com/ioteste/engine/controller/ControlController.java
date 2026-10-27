package com.ioteste.engine.controller;

import com.ioteste.engine.domain.ComandoControl;
import com.ioteste.engine.domain.EstadoControl;
import com.ioteste.engine.service.EvaluadorJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/control")
public class ControlController {
    
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
        return ResponseEntity.ok(new EstadoControl(estado, fechaHora, factorTiempo, iniciadoEn));
    }

    @PostMapping("/comandos")
    public ResponseEntity<EstadoControl> comandarControl(@RequestBody ComandoControl comando) {
        if ("INICIAR".equals(comando.accion())) {
            // El contrato exige un 400 si se envía INICIAR sin una fechaHora
            if (comando.fechaHora() == null) {
                return ResponseEntity.badRequest().build(); 
            }
            
            this.estado = "EN_EJECUCION";
            this.fechaHora = comando.fechaHora();
            this.factorTiempo = (comando.factorTiempo() != null) ? comando.factorTiempo() : 1.0;
            
            // Genera la hora real del servidor en formato ISO-8601 (UTC)
            this.iniciadoEn = Instant.now().atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
            
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