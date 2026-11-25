package com.ndonozo.servicioturnosreservas.reservation.domain.model;

public sealed interface ConfirmReservationResult {
    record Confirmed(ReservationProcess process) implements ConfirmReservationResult {}
    record NotFound() implements ConfirmReservationResult {}
    record InvalidState() implements ConfirmReservationResult {}
    record HoldExpired() implements ConfirmReservationResult {}
    record SlotAlreadyReserved() implements ConfirmReservationResult {}
}
