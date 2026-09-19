package com.ioteste.api.Controller;

import com.ioteste.api.Domain.Habitacion;
import com.ioteste.api.Repository.HabitacionRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/habitaciones")
public class HabitacionController {

    private final HabitacionRepository repository;

    public HabitacionController() {
        repository = new HabitacionRepository();
    }

    @GetMapping
    public ResponseEntity<List<Habitacion>> listar() {
        return ResponseEntity.ok(repository.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Habitacion> buscarPorId(@PathVariable("id") int id) {
        Habitacion habitacion = repository.buscarPorId(id);
        if (habitacion == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(habitacion);
    }

    @PostMapping
    public ResponseEntity<Habitacion> crear(@RequestBody Habitacion habitacion) {
        Habitacion habitacionCreada = repository.crearHabitacion(habitacion);
        return ResponseEntity.status(201).body(habitacionCreada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Habitacion> actualizar(@PathVariable("id") int id, @RequestBody Habitacion habitacion) {
        if (habitacion.getNombre() == null || habitacion.getTemperaturaEsperada() == null ||
                habitacion.getIdTermostato() == null || habitacion.getIdSwitch() == null) {
            return ResponseEntity.badRequest().build();
        }
        habitacion.setId(id);
        int codigo = repository.actualizarHabitacion(habitacion);
        if (codigo == 404) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(repository.buscarPorId(id));

    }

    @PatchMapping("/{id}")
    public ResponseEntity<Habitacion> actualizarParcial(@PathVariable("id") int id, @RequestBody Habitacion habitacion) {
        habitacion.setId(id);
        int codigo = repository.actualizarHabitacion(habitacion);

        if(codigo == 400){
            return ResponseEntity.badRequest().build();
        }
        if (codigo == 404){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(repository.buscarPorId(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable("id") int id) {
        boolean eliminada = repository.eliminarHabitacion(id);
        if(eliminada) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}