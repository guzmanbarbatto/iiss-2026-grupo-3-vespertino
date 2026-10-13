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

CREATE TABLE IF NOT EXISTS sitio_configuracion (
    clave INTEGER PRIMARY KEY CHECK (clave = 1),
    id VARCHAR(100) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    potencia_contratada_kw NUMERIC(8,2) NOT NULL
    CHECK (potencia_contratada_kw > 0),
    schema_version VARCHAR(20) NOT NULL
    );

CREATE TABLE IF NOT EXISTS tarifa_punta (
    sitio_clave INTEGER PRIMARY KEY
 REFERENCES sitio_configuracion(clave)
    ON DELETE CASCADE,
    desde TIME NOT NULL,
    hasta TIME NOT NULL,
    dias VARCHAR(10) NOT NULL
    CHECK (dias IN ('HABILES', 'TODOS')),
    CHECK (desde < hasta)
    );

CREATE TABLE IF NOT EXISTS sitio_habitacion (
    id VARCHAR(100) PRIMARY KEY,
    sitio_clave INTEGER NOT NULL
    REFERENCES sitio_configuracion(clave)
    ON DELETE CASCADE,
    nombre VARCHAR(50) NOT NULL,
    temperatura_esperada NUMERIC(4,1) NOT NULL,
    potencia_kw NUMERIC(8,3) NOT NULL
    CHECK (potencia_kw > 0),
    termostato_id VARCHAR(100) NOT NULL UNIQUE,
    topic_termostato VARCHAR(255) NOT NULL,
    switch_id VARCHAR(100) NOT NULL UNIQUE,
    url_switch TEXT NOT NULL,
    CONSTRAINT sitio_habitacion_nombre_no_vacio
    CHECK (LENGTH(TRIM(nombre)) > 0)
    );
