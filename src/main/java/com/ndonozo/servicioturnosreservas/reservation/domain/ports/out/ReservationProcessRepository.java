package com.ndonozo.servicioturnosreservas.reservation.domain.ports.out;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;

public interface ReservationProcessRepository {
    ReservationProcess save(ReservationProcess process);
}
