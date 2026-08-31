CREATE TABLE IF NOT EXISTS habitacion (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    termostato_id VARCHAR(50) NOT NULL,
    switch_id VARCHAR(50) NOT NULL,
    temperatura_objetivo NUMERIC(4,1) NOT NULL
);

CREATE TABLE IF NOT EXISTS historico_temperatura (
    id SERIAL PRIMARY KEY,
    habitacion_id INTEGER NOT NULL REFERENCES habitacion(id),
    fecha_hora TIMESTAMP NOT NULL,
    temperatura_c NUMERIC(4,1) NOT NULL
);

-- Insertamos la habitación ID 0 (que coincide con el payload de prueba)
INSERT INTO habitacion (id, nombre, termostato_id, switch_id, temperatura_objetivo)
VALUES (0, 'Habitación Principal', 'ht-sim-room1', 'sw-sim-room1', 22.0)
ON CONFLICT (id) DO NOTHING;