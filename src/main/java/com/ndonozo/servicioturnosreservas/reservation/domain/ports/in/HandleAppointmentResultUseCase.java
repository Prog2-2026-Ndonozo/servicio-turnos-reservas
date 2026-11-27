package com.ndonozo.servicioturnosreservas.reservation.domain.ports.in;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultEvent;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultOutcome;

public interface HandleAppointmentResultUseCase {
    AppointmentResultOutcome handleResult(AppointmentResultEvent event);
}
