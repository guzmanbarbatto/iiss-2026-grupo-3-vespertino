package com.ioteste.engine.controller;

import com.ioteste.engine.domain.ErrorRespuesta;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.repository.SitioRepository;
import com.ioteste.engine.service.ValidadorInventario;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** §5.1: inventario del sitio. Sin caché: {@code GET} devuelve siempre lo persistido. */
@RestController
@RequestMapping("/sitio")
public class SitioController {

    private static final Logger LOG = LoggerFactory.getLogger(SitioController.class);

    private final SitioRepository repository;
    private final ValidadorInventario validador;

    public SitioController(SitioRepository repository, ValidadorInventario validador) {
        this.repository = repository;
        this.validador = validador;
    }

    @GetMapping
    public ResponseEntity<?> obtenerSitio() {
        return repository.obtenerInventario()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorRespuesta.de(404, "todavía no se cargó ningún sitio")));
    }

    /** Atómico: se valida entero antes de tocar la base, y se graba en una sola transacción. */
    @PutMapping
    public ResponseEntity<?> reemplazarSitio(@RequestBody InventarioDTO inventario) {
        List<String> errores = validador.validar(inventario);
        if (!errores.isEmpty()) {
            return ResponseEntity.badRequest().body(ErrorRespuesta.de(400, "inventario inválido", errores));
        }
        repository.guardarInventario(inventario);
        LOG.info("Sitio '{}' cargado con {} habitaciones", inventario.sitio().id(), inventario.habitaciones().size());
        return ResponseEntity.ok(Map.of(
                "sitio", inventario.sitio().id(),
                "habitaciones", inventario.habitaciones().size()));
    }
}
