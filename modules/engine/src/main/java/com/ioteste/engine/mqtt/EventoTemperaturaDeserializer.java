package com.ioteste.subscriber.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.subscriber.Domain.EventoTemperatura;

import java.io.IOException;

public class EventoTemperaturaDeserializer {

    private final ObjectMapper mapper;

    public  EventoTemperaturaDeserializer() {
        mapper = new ObjectMapper();
    }

    public EventoTemperatura deserializar(byte[] payload) throws IOException {
        return mapper.readValue(payload, EventoTemperatura.class);
    }
}
