package com.ioteste.engine.domain;

import java.util.Optional;

/**
 * Evento que se publica después de grabar un {@code PUT /sitio}. Lo escuchan el motor (apaga los switches de
 * las habitaciones que salieron) y el suscriptor MQTT (se suscribe a los topics nuevos y deja los que sobran).
 */
public record InventarioReemplazado(
    Optional<InventarioDTO> anterior,
    InventarioDTO nuevo
) { }
