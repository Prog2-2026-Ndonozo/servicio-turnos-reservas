-- 9b: el identificador de la reserva confirmada (lo necesita cancelar, T11) y el motivo del último rechazo del teléfono.
ALTER TABLE reservation_process
    ADD COLUMN reservation_id BIGINT,
    ADD COLUMN rejection_reason VARCHAR(100);
