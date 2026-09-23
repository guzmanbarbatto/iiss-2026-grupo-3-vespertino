package com.ioteste.api.Controller;

import com.ioteste.api.Domain.ComandoControlador;
import com.ioteste.api.Service.TermostatoService;
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
            termostatoService.iniciarSuscripcion();
            return ResponseEntity.ok().build();
        } else if ("PARAR".equals(comando.getAccion())) {
            termostatoService.apagar();
            return ResponseEntity.ok().build();
        }
        
        return ResponseEntity.badRequest().build();
    }
}