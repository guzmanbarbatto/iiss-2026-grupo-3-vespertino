package com.ioteste.engine.controller;

import com.ioteste.engine.domain.AccionSwitch;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

@RestController
@RequestMapping("/switches")
public class SwitchController {

    private static final Logger LOGGER = Logger.getLogger(SwitchController.class.getName());

    // TO-DO: Este endpoint era el Stub de la Iteración 3. Será reemplazado por el simulador. Posiblemente se deba eliminar.
    @PostMapping("/{idSwitch}/acciones")
    public ResponseEntity<Void> accionar(@PathVariable("idSwitch") String idSwitch,
                                         @RequestBody AccionSwitch accion) {

        if (accion.getAccion() == null ||
                (!accion.getAccion().equals("ON") && !accion.getAccion().equals("OFF"))) {
            return ResponseEntity.badRequest().build();
        }
        LOGGER.info("Switch " + idSwitch + ": " + accion.getAccion());
        return ResponseEntity.ok().build();
    }
}