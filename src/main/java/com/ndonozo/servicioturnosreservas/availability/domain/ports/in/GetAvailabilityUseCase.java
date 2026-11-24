package com.ndonozo.servicioturnosreservas.availability.domain.ports.in;

import com.ndonozo.servicioturnosreservas.availability.domain.model.AvailabilityResult;

import java.time.LocalDate;

public interface GetAvailabilityUseCase {
    AvailabilityResult getAvailability(long professionalId, LocalDate from, LocalDate to);
}
