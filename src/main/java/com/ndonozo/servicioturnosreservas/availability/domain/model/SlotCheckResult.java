package com.ndonozo.servicioturnosreservas.availability.domain.model;

public sealed interface SlotCheckResult {
    record Reservable() implements SlotCheckResult {}
    record InvalidSlot() implements SlotCheckResult {}
    record ProfessionalDisabled() implements SlotCheckResult {}
    record ProfessionalNotFound() implements SlotCheckResult {}
    record CatalogUnavailable() implements SlotCheckResult {}
    record CatalogFailed() implements SlotCheckResult {}
}