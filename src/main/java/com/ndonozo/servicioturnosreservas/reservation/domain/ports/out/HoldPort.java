package com.ndonozo.servicioturnosreservas.reservation.domain.ports.out;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmHoldResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.HoldResult;

import java.time.LocalDate;
import java.time.LocalTime;

public interface HoldPort {
    HoldResult createHold(long professionalId, LocalDate date, LocalTime startTime, String externalPatientId);

    ConfirmHoldResult confirmHold(String holdId, String externalPatientId, String firstName, String lastName);
}
