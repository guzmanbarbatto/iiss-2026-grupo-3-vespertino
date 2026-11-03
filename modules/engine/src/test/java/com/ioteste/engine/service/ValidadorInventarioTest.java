package com.ioteste.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ioteste.engine.domain.Franja;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.Sitio;
import com.ioteste.engine.domain.Tarifa;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidadorInventarioTest {

    private final ValidadorInventario validador = new ValidadorInventario();

    /** El inventario de ejemplo del contrato (§1). */
    static InventarioDTO ejemploDelContrato() {
        return new InventarioDTO("2.0",
                new Sitio("casa-rodriguez", "Casa Rodríguez", 3.7, new Tarifa(new Franja("17:00", "23:00", "HABILES"))),
                List.of(
                        new Habitacion("room1", "Living", 21.5, 1.2, "ht-sim-room1",
                                "ht-sim-room1/status/temperature:0", "sw-sim-room1", "http://switches:8081"),
                        new Habitacion("room2", "Dormitorio principal", 20.0, 0.8, "ht-sim-room2",
                                "ht-sim-room2/status/temperature:0", "sw-sim-room2", "http://switches:8081")));
    }

    @Test
    void elEjemploDelContratoEsValido() {
        assertThat(validador.validar(ejemploDelContrato())).isEmpty();
    }

    @Test
    void cuerpoNulo() {
        assertThat(validador.validar(null)).containsExactly("el cuerpo es obligatorio");
    }

    @Test
    void rechazaUnMajorDeVersionDistinto() {
        assertThat(validador.validar(conVersion("3.0"))).anyMatch(e -> e.startsWith("schemaVersion"));
        assertThat(validador.validar(conVersion("2"))).anyMatch(e -> e.startsWith("schemaVersion"));
        assertThat(validador.validar(conVersion(null))).anyMatch(e -> e.startsWith("schemaVersion"));
        assertThat(validador.validar(conVersion("2.1"))).isEmpty();
    }

    @Test
    void sitioYHabitacionesSonObligatorios() {
        assertThat(validador.validar(new InventarioDTO("2.0", null, null)))
                .containsExactly("sitio es obligatorio", "habitaciones es obligatorio");
    }

    @Test
    void camposDelSitio() {
        Tarifa tarifa = new Tarifa(new Franja("17:00", "23:00", "TODOS"));
        InventarioDTO inventario = conSitio(new Sitio(" ", null, 0.0, tarifa));

        assertThat(validador.validar(inventario)).containsExactly(
                "sitio.id es obligatorio",
                "sitio.nombre es obligatorio",
                "sitio.potenciaContratadaKW debe ser > 0 (es 0.0)");
    }

    @Test
    void laTarifaEsObligatoria() {
        assertThat(validador.validar(conSitio(new Sitio("s", "Casa", 3.7, null))))
                .containsExactly("sitio.tarifa.punta es obligatoria");
        assertThat(validador.validar(conSitio(new Sitio("s", "Casa", 3.7, new Tarifa(null)))))
                .containsExactly("sitio.tarifa.punta es obligatoria");
    }

    @Test
    void formatoDeLaFranja() {
        assertThat(validador.validar(conFranja(new Franja("7:00", "25:00", "LUNES")))).containsExactly(
                "tarifa.punta.desde debe tener formato HH:mm, es '7:00'",
                "tarifa.punta.hasta debe tener formato HH:mm, es '25:00'",
                "tarifa.punta.dias debe ser HABILES o TODOS, es 'LUNES'");
    }

    @Test
    void laFranjaNoCruzaLaMedianocheNiEsVacia() {
        assertThat(validador.validar(conFranja(new Franja("23:00", "06:00", "TODOS"))))
                .containsExactly("tarifa.punta: desde (23:00) debe ser anterior a hasta (06:00)");
        assertThat(validador.validar(conFranja(new Franja("17:00", "17:00", "TODOS")))).hasSize(1);
    }

    @Test
    void potenciaDeHabitacionConElMensajeDelContrato() {
        Habitacion room2 = new Habitacion("room2", "Dormitorio", 20.0, 0.0, "ht-2", "t2", "sw-2", "http://sim:9090");

        assertThat(validador.validar(conHabitaciones(room2)))
                .containsExactly("habitación 'room2': potenciaKW debe ser > 0 (es 0.0)");
    }

    @Test
    void camposObligatoriosDeLaHabitacion() {
        Habitacion vacia = new Habitacion(null, "", null, null, null, " ", null, null);

        assertThat(validador.validar(conHabitaciones(vacia))).containsExactly(
                "habitación '#1': id es obligatorio",
                "habitación '#1': nombre es obligatorio",
                "habitación '#1': temperaturaEsperada es obligatoria",
                "habitación '#1': potenciaKW es obligatorio",
                "habitación '#1': idTermostato es obligatorio",
                "habitación '#1': topicTermostato es obligatorio",
                "habitación '#1': idSwitch es obligatorio",
                "habitación '#1': urlSwitch es obligatorio");
    }

    @Test
    void nombreLargoUrlInvalidaYHabitacionNula() {
        Habitacion rara = new Habitacion("r", "x".repeat(51), 20.0, 1.0, "ht", "t", "sw", "no es una url");

        assertThat(validador.validar(conHabitaciones(rara, null))).containsExactly(
                "habitación 'r': nombre admite hasta 50 caracteres",
                "habitación 'r': urlSwitch no es una URL válida ('no es una url')",
                "habitación #2: no puede ser null");
    }

    @Test
    void idsDuplicadosSeReportanUnaVezCadaUno() {
        Habitacion a = new Habitacion("room1", "A", 20.0, 1.0, "ht-1", "t1", "sw-sim-room1", "http://sim:9090");
        Habitacion b = new Habitacion("room1", "B", 20.0, 1.0, "ht-1", "t2", "sw-sim-room1", "http://sim:9090");
        Habitacion c = new Habitacion("room3", "C", 20.0, 1.0, "ht-3", "t3", "sw-sim-room1", "http://sim:9090");

        assertThat(validador.validar(conHabitaciones(a, b, c))).containsExactly(
                "id duplicado: 'room1'",
                "idTermostato duplicado: 'ht-1'",
                "idSwitch duplicado: 'sw-sim-room1'");
    }

    @Test
    void unSitioSinHabitacionesEsValido() {
        assertThat(validador.validar(conHabitaciones())).isEmpty();
    }

    private static InventarioDTO conVersion(String version) {
        InventarioDTO base = ejemploDelContrato();
        return new InventarioDTO(version, base.sitio(), base.habitaciones());
    }

    private static InventarioDTO conSitio(Sitio sitio) {
        return new InventarioDTO("2.0", sitio, List.of());
    }

    private static InventarioDTO conFranja(Franja franja) {
        return conSitio(new Sitio("s", "Casa", 3.7, new Tarifa(franja)));
    }

    private static InventarioDTO conHabitaciones(Habitacion... habitaciones) {
        InventarioDTO base = ejemploDelContrato();
        return new InventarioDTO("2.0", base.sitio(), new ArrayList<>(Arrays.asList(habitaciones)));
    }
}
