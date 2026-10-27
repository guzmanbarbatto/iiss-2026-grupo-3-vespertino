package com.ioteste.core.service;

import com.ioteste.core.domain.AccionSwitch;
import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Orden;
import com.ioteste.core.domain.Salida;
import com.ioteste.core.domain.TarifaPunta;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class EvaluadorCore {

    public Salida evaluarEstado(
            ZonedDateTime fechaHora,
            double potenciaContratadaKW,
            TarifaPunta tarifa,
            List<HabitacionDTO> habitaciones) {

        List<Orden> ordenes = new ArrayList<>();
        boolean esPunta = enTarifaPunta(fechaHora, tarifa);
        double potenciaEnUso = 0.0;

        // Primero calculamos cuánta potencia ya está tomada por habitaciones encendidas
        for (HabitacionDTO hab : habitaciones) {
            if (hab.estadoActual() == EstadoSwitch.ON) {
                potenciaEnUso += hab.potenciaKW();
            }
        }

        // Evaluamos habitación por habitación
        for (HabitacionDTO hab : habitaciones) {
            boolean necesitaCalor = hab.temperaturaActual() < hab.temperaturaEsperada();

            if (esPunta) {
                // REGLA 1: Si es tarifa punta, apagar todo lo que esté prendido
                if (hab.estadoActual() == EstadoSwitch.ON) {
                    ordenes.add(new Orden(hab.id(), AccionSwitch.OFF));
                    potenciaEnUso -= hab.potenciaKW(); // Liberamos potencia
                }
            } else {
                if (!necesitaCalor && hab.estadoActual() == EstadoSwitch.ON) {
                    // REGLA 2: Si ya llegó a la temperatura, apagar
                    ordenes.add(new Orden(hab.id(), AccionSwitch.OFF));
                    potenciaEnUso -= hab.potenciaKW(); // Liberamos potencia
                } else if (necesitaCalor && hab.estadoActual() == EstadoSwitch.OFF) {
                    // REGLA 3: Si tiene frío, prender SOLO si la llave general lo soporta
                    if ((potenciaEnUso + hab.potenciaKW()) <= potenciaContratadaKW) {
                        ordenes.add(new Orden(hab.id(), AccionSwitch.ON));
                        potenciaEnUso += hab.potenciaKW(); // Ocupamos potencia
                    }
                }
            }
        }

        return new Salida(ordenes);
    }

    private boolean enTarifaPunta(ZonedDateTime fechaHora, TarifaPunta tarifa) {
        LocalTime horaActual = fechaHora.toLocalTime();
        DayOfWeek diaActual = fechaHora.getDayOfWeek();

        // Si el día de hoy no está en la lista de días de la tarifa, nunca es punta
        if (!tarifa.dias().contains(diaActual)) {
            return false;
        }

        // Verificamos si la hora cae dentro del rango semiabierto [desde, hasta)
        return !horaActual.isBefore(tarifa.desde()) && horaActual.isBefore(tarifa.hasta());
    }
}