package com.ndonozo.servicioturnosreservas.reservation.domain.model;

public sealed interface CreateReservationResult {
    record Created(ReservationProcess process) implements CreateReservationResult {}
    record InvalidSlot() implements CreateReservationResult {}
    record ProfessionalDisabled() implements CreateReservationResult {}
    record ProfessionalNotFound() implements CreateReservationResult {}
    record SlotAlreadyHeld() implements CreateReservationResult {}
    record SlotAlreadyReserved() implements CreateReservationResult {}
    record CatalogUnavailable() implements CreateReservationResult {}
    record CatalogFailed() implements CreateReservationResult {}
}
