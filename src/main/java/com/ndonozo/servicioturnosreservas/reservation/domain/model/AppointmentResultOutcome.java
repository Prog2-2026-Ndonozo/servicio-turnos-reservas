package com.ndonozo.servicioturnosreservas.reservation.domain.model;

public sealed interface AppointmentResultOutcome {
    record Applied(ReservationProcess process) implements AppointmentResultOutcome {}
    record Duplicate() implements AppointmentResultOutcome {}
    record ProcessNotFound() implements AppointmentResultOutcome {}
    record HoldMismatch() implements AppointmentResultOutcome {}
    record NotApplicable() implements AppointmentResultOutcome {}
}
