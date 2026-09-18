package com.ioteste.api.Controller;

import com.ioteste.api.Domain.Habitacion;
import com.ioteste.api.Repository.HabitacionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/habitaciones")
public class HabitacionController {

    private final HabitacionRepository repository;

    public HabitacionController() {
        repository = new HabitacionRepository();
    }

    @GetMapping
    public List<Habitacion> listar() {
        return repository.listar();
    }

    @GetMapping("/{id}")
    public Habitacion buscarPorId(@PathVariable("id") int id) {
        return repository.buscarPorId(id);
    }

    @PostMapping
    public Habitacion crear(@RequestBody Habitacion habitacion) {
        return repository.crearHabitacion(habitacion);
    }

    @PutMapping("/{id}")
    public Habitacion actualizar(@PathVariable("id") int id, @RequestBody Habitacion habitacion) {
        habitacion.setId(id);
        repository.actualizarHabitacion(habitacion);
        return repository.buscarPorId(id);
    }

    @PatchMapping("/{id}")
    public Habitacion actualizarParcial(@PathVariable("id") int id, @RequestBody Habitacion habitacion) {
        habitacion.setId(id);
        repository.actualizarHabitacion(habitacion);
        return repository.buscarPorId(id);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable("id") int id) {
        repository.eliminarHabitacion(id);
    }
}