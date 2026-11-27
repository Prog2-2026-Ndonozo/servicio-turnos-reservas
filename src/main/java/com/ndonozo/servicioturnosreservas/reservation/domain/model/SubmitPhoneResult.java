package com.ndonozo.servicioturnosreservas.reservation.domain.model;

public sealed interface SubmitPhoneResult {
    record Submitted(ReservationProcess process) implements SubmitPhoneResult {}
    record NotFound() implements SubmitPhoneResult {}
    record InvalidState() implements SubmitPhoneResult {}
    record PhoneNotRequested() implements SubmitPhoneResult {}
    record HoldExpired() implements SubmitPhoneResult {}
    record InvalidPhone() implements SubmitPhoneResult {}
}
