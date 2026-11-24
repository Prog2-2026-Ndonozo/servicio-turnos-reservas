package com.ndonozo.servicioturnosreservas.availability.domain.model;

import java.time.LocalDate;
import java.util.List;

public record Availability(
        long professionalId,
        LocalDate from,
        LocalDate to,
        List<AvailabilitySlot> slots
) {
}
