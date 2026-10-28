-- Estructura compatible con VIA COSTA v1 (no elimina datos previos).
CREATE TABLE IF NOT EXISTS usuarios (
 id BIGSERIAL PRIMARY KEY,
 usuario VARCHAR(50) NOT NULL UNIQUE,
 password_hash TEXT NOT NULL,
 rol VARCHAR(12) NOT NULL CHECK (rol IN ('ADMIN','EMPLEADO')),
 activo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS pasajeros (
 id BIGSERIAL PRIMARY KEY,
 dni CHAR(8) NOT NULL UNIQUE CHECK (dni ~ '^[0-9]{8}$'),
 nombres VARCHAR(120) NOT NULL,
 apellidos VARCHAR(120) NOT NULL,
 telefono VARCHAR(20),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS buses (
 id BIGSERIAL PRIMARY KEY,
 placa VARCHAR(12) NOT NULL UNIQUE,
 capacidad INTEGER NOT NULL CHECK (capacidad > 0),
 modelo VARCHAR(100)
);
CREATE TABLE IF NOT EXISTS rutas (
 id BIGSERIAL PRIMARY KEY,
 origen VARCHAR(100) NOT NULL,
 destino VARCHAR(100) NOT NULL,
 duracion_horas NUMERIC(5,2) CHECK (duracion_horas > 0)
);
CREATE TABLE IF NOT EXISTS viajes (
 id BIGSERIAL PRIMARY KEY,
 ruta_id BIGINT NOT NULL REFERENCES rutas(id),
 bus_id BIGINT NOT NULL REFERENCES buses(id),
 salida TIMESTAMP NOT NULL,
 precio NUMERIC(10,2) NOT NULL CHECK (precio >= 0),
 estado VARCHAR(15) NOT NULL DEFAULT 'PROGRAMADO' CHECK (estado IN ('PROGRAMADO','CANCELADO','FINALIZADO')),
 UNIQUE (bus_id, salida)
);
CREATE TABLE IF NOT EXISTS ventas (
 id BIGSERIAL PRIMARY KEY,
 viaje_id BIGINT NOT NULL REFERENCES viajes(id),
 pasajero_id BIGINT NOT NULL REFERENCES pasajeros(id),
 empleado_id BIGINT NOT NULL REFERENCES usuarios(id),
 asiento INTEGER NOT NULL CHECK (asiento > 0),
 monto NUMERIC(10,2) NOT NULL CHECK (monto >= 0),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 anulada BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS asiento_activo_unico ON ventas(viaje_id,asiento) WHERE anulada = FALSE;
CREATE INDEX IF NOT EXISTS ventas_fecha_idx ON ventas(creado_en);
CREATE INDEX IF NOT EXISTS viajes_salida_idx ON viajes(salida);
