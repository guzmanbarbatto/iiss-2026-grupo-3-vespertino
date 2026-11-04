package com.ioteste.core.service;

import com.ioteste.core.domain.AccionSwitch;
import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Orden;
import com.ioteste.core.domain.Salida;
import com.ioteste.core.domain.TarifaPunta;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EvaluadorCore {

    /**
     * Margen (°C) por encima de la temperatura esperada a partir del cual se apaga una habitación encendida.
     * Se enciende apenas la temperatura baja de la esperada, pero no se apaga hasta superarla por este
     * margen: sin él, una lectura que oscila alrededor del objetivo prendería y apagaría el relé en cada ciclo.
     */
    public static final double HISTERESIS_C = 0.5;

    /** Tolerancia de redondeo al comparar potencias: con {@code double}, 1,1 + 2,2 da 3,3000000000000003. */
    private static final double TOLERANCIA_KW = 1e-9;

    /**
     * Con más habitaciones con frío que esto, solo se combinan las de más prioridad (2^16 combinaciones como
     * mucho, y la búsqueda corta antes casi siempre); las demás se suman después, si entran.
     */
    static final int MAX_A_COMBINAR = 16;

    /** Primero la habitación más lejos de su objetivo; entre iguales, el orden del inventario. */
    private static final Comparator<HabitacionDTO> POR_PRIORIDAD =
            Comparator.comparingDouble(EvaluadorCore::falta).reversed();

    /** Las órdenes del ciclo sin anticipar la tarifa punta (ver la variante con {@code anticipacion}). */
    public Salida evaluarEstado(
            ZonedDateTime fechaHora,
            double potenciaContratadaKW,
            TarifaPunta tarifa,
            List<HabitacionDTO> habitaciones) {
        return evaluarEstado(fechaHora, Duration.ZERO, potenciaContratadaKW, tarifa, habitaciones);
    }

    /**
     * Calcula las órdenes del ciclo. Las de apagado van siempre antes que las de encendido, para que la
     * potencia se libere antes de volver a ocuparla.
     *
     * @param anticipacion cuánto puede faltar para la próxima evaluación. Si la tarifa punta empieza antes, se
     *     apaga ya: esperar a la evaluación siguiente dejaría calefactores encendidos dentro de la franja.
     */
    public Salida evaluarEstado(
            ZonedDateTime fechaHora,
            Duration anticipacion,
            double potenciaContratadaKW,
            TarifaPunta tarifa,
            List<HabitacionDTO> habitaciones) {

        List<Orden> apagar = new ArrayList<>();
        List<Orden> encender = new ArrayList<>();

        if (enTarifaPunta(fechaHora, tarifa) || enTarifaPunta(fechaHora.plus(anticipacion), tarifa)) {
            // REGLA 1: en tarifa punta (o si empieza antes de la próxima evaluación) se apaga todo lo que esté
            // (o pueda estar) encendido
            for (HabitacionDTO hab : habitaciones) {
                if (hab.estadoActual() != EstadoSwitch.OFF) {
                    apagar.add(new Orden(hab.id(), AccionSwitch.OFF));
                }
            }
            return new Salida(apagar);
        }

        double potenciaEnUso = 0.0;
        List<HabitacionDTO> encendidas = new ArrayList<>();
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
                        encendidas.add(hab);
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

        // REGLA 3: si lo encendido ya no entra en la contratada (por ejemplo, porque bajó con un PUT /sitio en
        // caliente), siguen encendidas las que más la aprovechan (entre combinaciones iguales, las más lejos de su
        // objetivo) y se apaga el resto, empezando por la más cerca de su objetivo
        if (potenciaEnUso > potenciaContratadaKW + TOLERANCIA_KW) {
            encendidas.sort(POR_PRIORIDAD);
            Set<String> siguen = ids(mejorCombinacion(encendidas, potenciaContratadaKW));
            List<HabitacionDTO> sobran = new ArrayList<>();
            potenciaEnUso = 0.0;
            for (HabitacionDTO hab : encendidas) {
                if (siguen.contains(hab.id())) {
                    potenciaEnUso += hab.potenciaKW();
                } else {
                    sobran.add(hab);
                }
            }
            sobran.sort(Comparator.comparingDouble(EvaluadorCore::falta));
            sobran.forEach(hab -> apagar.add(new Orden(hab.id(), AccionSwitch.OFF)));
        }

        // REGLA 4: con la potencia libre, encender la combinación de habitaciones con frío que más la aprovecha;
        // entre combinaciones iguales, la que incluye a las más lejos de su objetivo. Las órdenes salen en ese
        // orden de prioridad
        conFrio.sort(POR_PRIORIDAD);
        Set<String> aEncender = ids(mejorCombinacion(conFrio, potenciaContratadaKW - potenciaEnUso));

        for (HabitacionDTO hab : conFrio) {
            if (aEncender.contains(hab.id())) {
                encender.add(new Orden(hab.id(), AccionSwitch.ON));
            } else if (hab.estadoActual() == EstadoSwitch.DESCONOCIDO) {
                // No entra en la potencia y podría estar encendido: se apaga para no superar el tope
                apagar.add(new Orden(hab.id(), AccionSwitch.OFF));
            }
        }

        List<Orden> ordenes = new ArrayList<>(apagar);
        ordenes.addAll(encender);
        return new Salida(ordenes);
    }

    /**
     * De {@code candidatas}, ordenadas de mayor a menor prioridad, las que suman más potencia sin pasar de
     * {@code libreKW}. Entre combinaciones que suman lo mismo, la que incluye a las de más prioridad.
     *
     * <p>Tomarlas una por una, por prioridad, puede dejar potencia sin usar: con 2 kW libres y habitaciones
     * empatadas de 1,2, 0,9, 0,9 y 1,0 kW, la de 1,2 sola deja 0,8 kW donde no entra ninguna otra; la de 0,9 con
     * la de 1,0 usan 1,9. Se prueban las combinaciones de las {@link #MAX_A_COMBINAR} de más prioridad; si hay
     * más, las restantes se suman después, una por una, si entran.
     */
    static List<HabitacionDTO> mejorCombinacion(List<HabitacionDTO> candidatas, double libreKW) {
        List<HabitacionDTO> combinables = candidatas.subList(0, Math.min(candidatas.size(), MAX_A_COMBINAR));
        List<HabitacionDTO> elegidas = new BusquedaDeCombinacion(combinables, libreKW).mejor();
        double usada = elegidas.stream().mapToDouble(HabitacionDTO::potenciaKW).sum();
        for (HabitacionDTO hab : candidatas.subList(combinables.size(), candidatas.size())) {
            if (usada + hab.potenciaKW() <= libreKW + TOLERANCIA_KW) {
                elegidas.add(hab);
                usada += hab.potenciaKW();
            }
        }
        return elegidas;
    }

    /**
     * Recorre las combinaciones en profundidad, probando cada habitación primero incluida y después no: así, entre
     * las que suman lo mismo, la primera que encuentra es la que incluye a las de más prioridad. Corta las ramas
     * que no pueden superar a la mejor encontrada y termina si alguna ya llena lo libre.
     */
    private static final class BusquedaDeCombinacion {
        private final List<HabitacionDTO> candidatas;
        private final double libreKW;
        /** {@code restante[i]}: lo que suman las candidatas desde la {@code i} en adelante. */
        private final double[] restante;
        private final boolean[] actual;
        private boolean[] mejor;
        private double mejorSuma = -1;

        BusquedaDeCombinacion(List<HabitacionDTO> candidatas, double libreKW) {
            this.candidatas = candidatas;
            this.libreKW = libreKW;
            int n = candidatas.size();
            restante = new double[n + 1];
            for (int i = n - 1; i >= 0; i--) {
                restante[i] = restante[i + 1] + candidatas.get(i).potenciaKW();
            }
            actual = new boolean[n];
            mejor = new boolean[n];
        }

        List<HabitacionDTO> mejor() {
            buscar(0, 0.0);
            List<HabitacionDTO> elegidas = new ArrayList<>();
            for (int i = 0; i < mejor.length; i++) {
                if (mejor[i]) {
                    elegidas.add(candidatas.get(i));
                }
            }
            return elegidas;
        }

        private void buscar(int i, double suma) {
            if (i == candidatas.size()) {
                if (suma > mejorSuma + TOLERANCIA_KW) {
                    mejorSuma = suma;
                    mejor = actual.clone();
                }
                return;
            }
            if (suma + restante[i] <= mejorSuma + TOLERANCIA_KW || mejorSuma >= libreKW - TOLERANCIA_KW) {
                return; // esta rama no puede superar a la mejor, o la mejor ya llena lo libre
            }
            double potencia = candidatas.get(i).potenciaKW();
            if (suma + potencia <= libreKW + TOLERANCIA_KW) {
                actual[i] = true;
                buscar(i + 1, suma + potencia);
                actual[i] = false;
            }
            buscar(i + 1, suma);
        }
    }

    /** Cuántos grados le faltan para llegar a su objetivo (negativo si ya lo pasó). */
    private static double falta(HabitacionDTO hab) {
        return hab.temperaturaEsperada() - hab.temperaturaActual();
    }

    private static Set<String> ids(List<HabitacionDTO> habitaciones) {
        Set<String> ids = new HashSet<>();
        habitaciones.forEach(hab -> ids.add(hab.id()));
        return ids;
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
