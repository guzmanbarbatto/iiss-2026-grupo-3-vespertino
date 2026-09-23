package com.ioteste.subscriber.repository;

import com.ioteste.subscriber.Domain.Habitacion;
import java.util.HashMap;
import java.util.Map;

public class HabitacionRepository {

    private final Map<Integer, Habitacion> habitaciones;

    public HabitacionRepository() {
        this.habitaciones = new HashMap<>();
    }
    public void inicializar(){
        Habitacion habitacion = new Habitacion(
                0,"termostato-room1","switch-room1",21.5
        );
        guardarHabitacion(habitacion);
    }

    public void guardarHabitacion(Habitacion habitacion) {
        habitaciones.put(habitacion.getId(),habitacion);
    }
    public Habitacion buscarPorId(int id){
        return habitaciones.get(id);
    }
}
