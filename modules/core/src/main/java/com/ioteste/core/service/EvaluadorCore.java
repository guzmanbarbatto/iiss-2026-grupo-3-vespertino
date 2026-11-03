package com.ioteste.core.service;

import com.ioteste.core.domain.AccionSwitch;
import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Orden;
import com.ioteste.core.domain.Salida;
import com.ioteste.core.domain.TarifaPunta;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EvaluadorCore {

    /**
     * Margen (°C) por encima de la temperatura esperada a partir del cual se apaga una habitación encendida.
     * Se enciende apenas la temperatura baja de la esperada, pero no se apaga hasta superarla por este
     * margen: sin él, una lectura que oscila alrededor del objetivo prendería y apagaría el relé en cada ciclo.
     */
    public static final double HISTERESIS_C = 0.5;

    /**
     * Calcula las órdenes del ciclo. Las de apagado van siempre antes que las de encendido, para que la
     * potencia se libere antes de volver a ocuparla.
     */
    public Salida evaluarEstado(
            ZonedDateTime fechaHora,
            double potenciaContratadaKW,
            TarifaPunta tarifa,
            List<HabitacionDTO> habitaciones) {

        List<Orden> apagar = new ArrayList<>();
        List<Orden> encender = new ArrayList<>();

        if (enTarifaPunta(fechaHora, tarifa)) {
            // REGLA 1: en tarifa punta se apaga todo lo que esté (o pueda estar) encendido
            for (HabitacionDTO hab : habitaciones) {
                if (hab.estadoActual() != EstadoSwitch.OFF) {
                    apagar.add(new Orden(hab.id(), AccionSwitch.OFF));
                }
            }
            return new Salida(apagar);
        }

        double potenciaEnUso = 0.0;
        List<HabitacionDTO> conFrio = new ArrayList<>();

        for (HabitacionDTO hab : habitaciones) {
            boolean necesitaCalor = hab.temperaturaActual() < hab.temperaturaEsperada();
            boolean superoObjetivo = hab.temperaturaActual() >= hab.temperaturaEsperada() + HISTERESIS_C;

            switch (hab.estadoActual()) {
                case ON -> {
                    if (superoObjetivo) {
                        // REGLA 2: ya superó la temperatura esperada (más el margen): apagar y liberar potencia
                        apagar.add(new Orden(hab.id(), AccionSwitch.OFF));
                    } else {
                        potenciaEnUso += hab.potenciaKW();
                    }
                }
                case OFF -> {
                    if (necesitaCalor) {
                        conFrio.add(hab);
                    }
                }
                case DESCONOCIDO -> {
                    // Sin estado confiable se comanda siempre, para que el estado quede conocido
                    if (necesitaCalor) {
                        conFrio.add(hab);
                    } else {
                        apagar.add(new Orden(hab.id(), AccionSwitch.OFF));
                    }
                }
            }
        }

        // REGLA 3: encender las que tienen frío, primero la que está más lejos de su objetivo,
        // solo si la potencia contratada lo soporta
        conFrio.sort(Comparator.comparingDouble(
                (HabitacionDTO h) -> h.temperaturaEsperada() - h.temperaturaActual()).reversed());

        for (HabitacionDTO hab : conFrio) {
            if (potenciaEnUso + hab.potenciaKW() <= potenciaContratadaKW) {
                encender.add(new Orden(hab.id(), AccionSwitch.ON));
                potenciaEnUso += hab.potenciaKW();
            } else if (hab.estadoActual() == EstadoSwitch.DESCONOCIDO) {
                // No entra en la potencia y podría estar encendido: se apaga para no superar el tope
                apagar.add(new Orden(hab.id(), AccionSwitch.OFF));
            }
        }

        List<Orden> ordenes = new ArrayList<>(apagar);
        ordenes.addAll(encender);
        return new Salida(ordenes);
    }

    private boolean enTarifaPunta(ZonedDateTime fechaHora, TarifaPunta tarifa) {
        if (!tarifa.dias().contains(fechaHora.getDayOfWeek())) {
            return false;
        }

        LocalTime horaActual = fechaHora.toLocalTime();

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
