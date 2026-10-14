package com.ioteste.engine.domain;

import java.util.List;

public record InventarioDTO(
    String schemaVersion,
    Sitio sitio,
    List<Habitacion> habitaciones
) {}