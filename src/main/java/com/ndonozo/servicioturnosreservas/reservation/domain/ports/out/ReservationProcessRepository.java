package com.ndonozo.servicioturnosreservas.reservation.domain.ports.out;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;

import java.util.Optional;

public interface ReservationProcessRepository {
    /** Falla con ReservationConflictException si otro escritor guardó el proceso desde que se leyó. */
    ReservationProcess save(ReservationProcess process);

    /** Sin filtrar por usuario: lo usa quien reacciona a eventos de la cátedra, que no tienen usuario. */
    Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId);

    /** Solo devuelve el proceso si es del usuario: la propiedad por usuario se aplica acá (E §9). */
    Optional<ReservationProcess> findByReservationProcessIdAndUserId(String reservationProcessId, long userId);
}
