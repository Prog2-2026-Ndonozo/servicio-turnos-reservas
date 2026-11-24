package com.ndonozo.servicioturnosreservas.availability.infrastructure.web.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        long professionalId,
        LocalDate from,
        LocalDate to,
        List<SlotResponse> slots

) {
}
