CREATE TABLE IF NOT EXISTS habitacion (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    termostato_id VARCHAR(50) NOT NULL,
    switch_id VARCHAR(50) NOT NULL,
    temperatura_objetivo NUMERIC(4,1) NOT NULL,
    consumo NUMERIC(8,2) NOT NULL DEFAULT 0.0 
);

CREATE TABLE IF NOT EXISTS historico_temperatura (
    id SERIAL PRIMARY KEY,
    habitacion_id INTEGER NOT NULL REFERENCES habitacion(id),
    fecha_hora TIMESTAMP NOT NULL,
    temperatura_c NUMERIC(4,1) NOT NULL
);

-- Insertamos la habitación ID 0 con un consumo estimado (ej. 1500 watts)
INSERT INTO habitacion (id, nombre, termostato_id, switch_id, temperatura_objetivo, consumo)
VALUES (0, 'Habitación Principal', 'ht-sim-room1', 'sw-sim-room1', 22.0, 1500.0)
ON CONFLICT (id) DO NOTHING;