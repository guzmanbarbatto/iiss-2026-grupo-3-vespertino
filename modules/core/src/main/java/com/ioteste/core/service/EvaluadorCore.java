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

        // 1. Calculamos cuánta potencia ya está tomada por habitaciones encendidas
        for (HabitacionDTO hab : habitaciones) {
            if (hab.estadoActual() == EstadoSwitch.ON) {
                potenciaEnUso += hab.potenciaKW();
            }
        }

        if (esPunta) {
            // REGLA 1: Si es tarifa punta, apagar todo lo que esté prendido
            for (HabitacionDTO hab : habitaciones) {
                if (hab.estadoActual() == EstadoSwitch.ON) {
                    ordenes.add(new Orden(hab.id(), AccionSwitch.OFF));
                    potenciaEnUso -= hab.potenciaKW();
                }
            }
            return new Salida(ordenes);
        }

        // 2. Fuera de punta - PRIMERA PASADA: liberar potencia apagando las que ya alcanzaron temperatura
        for (HabitacionDTO hab : habitaciones) {
            boolean necesitaCalor = hab.temperaturaActual() < hab.temperaturaEsperada();
            if (!necesitaCalor && hab.estadoActual() == EstadoSwitch.ON) {
                // REGLA 2: Si ya llegó a la temperatura, apagar
                ordenes.add(new Orden(hab.id(), AccionSwitch.OFF));
                potenciaEnUso -= hab.potenciaKW();
            }
        }

        // 3. Fuera de punta - SEGUNDA PASADA: encender las que tienen frío si la potencia alcanza
        for (HabitacionDTO hab : habitaciones) {
            boolean necesitaCalor = hab.temperaturaActual() < hab.temperaturaEsperada();
            if (necesitaCalor && hab.estadoActual() == EstadoSwitch.OFF) {
                // REGLA 3: Si tiene frío, prender SOLO si la llave general lo soporta
                if ((potenciaEnUso + hab.potenciaKW()) <= potenciaContratadaKW) {
                    ordenes.add(new Orden(hab.id(), AccionSwitch.ON));
                    potenciaEnUso += hab.potenciaKW();
                }
            }
        }

        return new Salida(ordenes);
    }

    private boolean enTarifaPunta(ZonedDateTime fechaHora, TarifaPunta tarifa) {
        LocalTime horaActual = fechaHora.toLocalTime();
        DayOfWeek diaActual = fechaHora.getDayOfWeek();

        if (!tarifa.dias().contains(diaActual)) {
            return false;
        }

        // Si desde == hasta, la franja es vacía
        if (tarifa.desde().equals(tarifa.hasta())) {
            return false;
        }

        // Rango normal en el mismo día [desde, hasta) (ej. 17:00 a 23:00)
        if (tarifa.desde().isBefore(tarifa.hasta())) {
            return !horaActual.isBefore(tarifa.desde()) && horaActual.isBefore(tarifa.hasta());
        }

        // Rango nocturno que cruza medianoche (ej. 22:00 a 06:00)
        return !horaActual.isBefore(tarifa.desde()) || horaActual.isBefore(tarifa.hasta());
    }
}
