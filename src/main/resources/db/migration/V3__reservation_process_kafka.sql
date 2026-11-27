-- 8b: el pedido de teléfono de la cátedra, el bloqueo optimista y los eventos ya procesados.
ALTER TABLE reservation_process
    ADD COLUMN phone_request_event_id VARCHAR(100),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE processed_event (
    event_id     VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_processed_event_id_no_vacio CHECK (btrim(event_id) <> '')
);
