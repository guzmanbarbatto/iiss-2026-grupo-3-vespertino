package com.ioteste.engine.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ioteste.engine.service.SitioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sitio")
public class SitioController {

    private final SitioService service;

    public SitioController(SitioService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<JsonNode> obtener() {
        JsonNode sitio = service.obtenerSitio();

        if (sitio == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(sitio);
    }

    @PutMapping
    public ResponseEntity<Void> reemplazar(@RequestBody JsonNode sitio) {

        service.guardarSitio(sitio);
        return ResponseEntity.noContent().build();
    }
}
