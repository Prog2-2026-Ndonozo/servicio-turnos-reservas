package com.ndonozo.servicioturnosreservas.reservation.domain.model;

public sealed interface HoldResult {
    record Created(Hold hold) implements HoldResult {}
    record SlotAlreadyHeld() implements HoldResult {}
    record SlotAlreadyReserved() implements HoldResult {}
    record InvalidSlot() implements HoldResult {}
    record ProfessionalDisabled() implements HoldResult {}
    record ProfessionalNotFound() implements HoldResult {}
}
