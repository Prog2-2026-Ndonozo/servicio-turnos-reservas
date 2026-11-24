package com.ndonozo.servicioturnosreservas.availability.domain.ports.in;

import com.ndonozo.servicioturnosreservas.availability.domain.model.SlotCheckResult;

import java.time.LocalDate;
import java.time.LocalTime;

public interface CheckSlotReservableUseCase {
    SlotCheckResult checkSlot(long professionalId, LocalDate date, LocalTime startTime);
}
