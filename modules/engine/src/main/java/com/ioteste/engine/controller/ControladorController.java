package com.ioteste.engine.controller;

import com.ioteste.engine.domain.ComandoControlador; 
import com.ioteste.engine.service.TermostatoService; 
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/controlador")
public class ControladorController {

    private final TermostatoService termostatoService;

    public ControladorController(TermostatoService termostatoService) {
        this.termostatoService = termostatoService;
    }

    @PostMapping("/acciones")
    public ResponseEntity<Void> accionar(@RequestBody ComandoControlador comando) {
        if (comando == null || comando.getAccion() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        if ("INICIAR".equals(comando.getAccion())) {
            // TO-DO: Extraer la fecha/hora de sincronización del 'comando' y pasarla al Core.
            termostatoService.iniciarSuscripcion();
            return ResponseEntity.ok().build();
        } else if ("PARAR".equals(comando.getAccion())) {
            termostatoService.apagar();
            return ResponseEntity.ok().build();
        }
        
        return ResponseEntity.badRequest().build();
    }
}