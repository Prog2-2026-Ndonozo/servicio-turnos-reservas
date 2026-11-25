package com.ndonozo.servicioturnosreservas.reservation.domain.ports.out;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;

import java.util.Optional;

public interface ReservationProcessRepository {
    ReservationProcess save(ReservationProcess process);

    /** Solo devuelve el proceso si es del usuario: la propiedad por usuario se aplica acá (E §9). */
    Optional<ReservationProcess> findByReservationProcessIdAndUserId(String reservationProcessId, long userId);
}
