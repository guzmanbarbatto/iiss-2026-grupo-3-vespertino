package com.ioteste.engine.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
public record ComandoControl(
    @JsonProperty("accion") String accion,
    @JsonProperty("fechaHora") String fechaHora,
    @JsonProperty("factorTiempo") Double factorTiempo
) {}