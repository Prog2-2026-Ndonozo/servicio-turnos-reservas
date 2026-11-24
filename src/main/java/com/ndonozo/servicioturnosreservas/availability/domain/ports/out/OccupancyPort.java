package com.ndonozo.servicioturnosreservas.availability.domain.ports.out;

import com.ndonozo.servicioturnosreservas.availability.domain.model.Occupancy;

import java.time.LocalDate;
import java.util.List;

public interface OccupancyPort {
    List<Occupancy> findOccupancies(long professionalId, LocalDate from, LocalDate to);
}
