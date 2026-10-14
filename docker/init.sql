DROP TABLE IF EXISTS historico_temperatura CASCADE;
DROP TABLE IF EXISTS habitacion CASCADE;
DROP TABLE IF EXISTS sitio CASCADE;

-- Tabla para guardar el esquema Sitio de controlador.openapi-v2.yaml
CREATE TABLE sitio (
    id VARCHAR(50) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    potencia_contratada_kw NUMERIC(8,2) NOT NULL,
    tarifa_desde TIME NOT NULL,
    tarifa_hasta TIME NOT NULL,
    tarifa_dias VARCHAR(20) NOT NULL
);

-- Tabla para guardar el esquema Habitacion de controlador.openapi-v2.yaml
CREATE TABLE habitacion (
    id VARCHAR(50) PRIMARY KEY,
    sitio_id VARCHAR(50) REFERENCES sitio(id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    temperatura_esperada NUMERIC(4,1) NOT NULL,
    potencia_kw NUMERIC(8,2) NOT NULL,
    id_termostato VARCHAR(50) NOT NULL,
    topic_termostato VARCHAR(200) NOT NULL,
    id_switch VARCHAR(50) NOT NULL,
    url_switch VARCHAR(200) NOT NULL
);

CREATE TABLE historico_temperatura (
    id SERIAL PRIMARY KEY,
    habitacion_id VARCHAR(50) NOT NULL REFERENCES habitacion(id) ON DELETE CASCADE,
    fecha_hora TIMESTAMP NOT NULL,
    temperatura_c NUMERIC(4,1) NOT NULL
);

-- ==========================================
-- DATOS SEMILLA PARA EL SIMULADOR
-- ==========================================

INSERT INTO sitio (id, nombre, potencia_contratada_kw, tarifa_desde, tarifa_hasta, tarifa_dias) 
VALUES ('sitio-1', 'Casa Catedra', 100.0, '18:00', '22:00', 'MONDAY,TUESDAY');

INSERT INTO habitacion (id, sitio_id, nombre, temperatura_esperada, potencia_kw, id_termostato, topic_termostato, id_switch, url_switch) 
VALUES ('hab-1', 'sitio-1', 'Cuarto 1', 20.0, 2.0, 'ht-room1', 'ht-room1/status/temperature:0', 'sw-room1', 'http://host.docker.internal:9090');

INSERT INTO habitacion (id, sitio_id, nombre, temperatura_esperada, potencia_kw, id_termostato, topic_termostato, id_switch, url_switch) 
VALUES ('hab-2', 'sitio-1', 'Cuarto 2', 20.0, 2.0, 'ht-room2', 'ht-room2/status/temperature:0', 'sw-room2', 'http://host.docker.internal:9090');