package com.ioteste.api.Controller;

import com.ioteste.api.Domain.AccionSwitch;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.logging.Logger;


@RestController
@RequestMapping("/switches")
public class SwitchController {

    private static final Logger LOGGER = Logger.getLogger(SwitchController.class.getName());

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
