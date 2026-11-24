package com.ndonozo.servicioturnosreservas.availability.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilitySlot(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
}
