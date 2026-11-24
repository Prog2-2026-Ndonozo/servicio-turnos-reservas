package com.ndonozo.servicioturnosreservas.availability.domain.model;

public sealed interface AvailabilityResult {
    record Found(Availability availability) implements AvailabilityResult {}
    record InvalidRange(String message) implements AvailabilityResult {}
    record ProfessionalNotFound() implements AvailabilityResult {}
    record CatalogUnavailable() implements AvailabilityResult {}
    record CatalogFailed() implements AvailabilityResult {}

}
