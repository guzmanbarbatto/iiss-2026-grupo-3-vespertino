-- Esquema del engine. Es idempotente: se aplica en cada arranque y además actualiza las bases creadas
-- por versiones anteriores (columnas e índices nuevos).

-- Sitio del controlador (esquema Sitio de controlador.openapi-v2.yaml). Hay a lo sumo uno.
CREATE TABLE IF NOT EXISTS sitio (
    id VARCHAR(50) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    potencia_contratada_kw NUMERIC(8,2) NOT NULL,
    tarifa_desde TIME NOT NULL,
    tarifa_hasta TIME NOT NULL,
    tarifa_dias VARCHAR(20) NOT NULL
);
ALTER TABLE sitio ADD COLUMN IF NOT EXISTS schema_version VARCHAR(10) NOT NULL DEFAULT '2.0';

-- Habitaciones del sitio (esquema Habitacion). "orden" conserva el orden en que se cargaron.
CREATE TABLE IF NOT EXISTS habitacion (
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
ALTER TABLE habitacion ADD COLUMN IF NOT EXISTS orden INT NOT NULL DEFAULT 0;

-- Lecturas MQTT de los termostatos, en UTC. fecha_hora es el instante de la medición (ts del termostato);
-- recibida_en, cuándo llegó al engine (hora real): con eso se detecta un termostato que dejó de publicar.
CREATE TABLE IF NOT EXISTS historico_temperatura (
    id SERIAL PRIMARY KEY,
    habitacion_id VARCHAR(50) NOT NULL REFERENCES habitacion(id) ON DELETE CASCADE,
    fecha_hora TIMESTAMP NOT NULL,
    temperatura_c NUMERIC(4,1) NOT NULL
);
ALTER TABLE historico_temperatura ADD COLUMN IF NOT EXISTS recibida_en TIMESTAMP;

-- Una versión anterior apagaba los switches de las habitaciones que salían del inventario y los anotaba
-- acá. Ya no: el controlador comanda solo los switches del sitio vigente. Se borra la tabla para que una
-- base vieja no conserve pendientes que nadie va a leer.
DROP TABLE IF EXISTS switch_pendiente_apagado;

-- El ciclo de control y el MCP buscan la última lectura de cada habitación: sin este índice
-- cada consulta recorría todo el histórico, que crece sin límite. La última es la que LLEGÓ última,
-- no la de hora simulada mayor: cada INICIAR puede volver el reloj simulado atrás, y las lecturas de una
-- corrida anterior (con horas más tardías) taparían a las nuevas. Las filas anteriores a recibida_en usan
-- su fecha_hora.
CREATE INDEX IF NOT EXISTS idx_historico_habitacion_llegada
    ON historico_temperatura (habitacion_id, (COALESCE(recibida_en, fecha_hora)) DESC);
-- El índice anterior ordenaba por la hora simulada: ya no lo usa ninguna consulta.
DROP INDEX IF EXISTS idx_historico_habitacion_fecha;
