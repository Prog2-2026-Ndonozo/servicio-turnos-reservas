package com.ndonozo.servicioturnosreservas.reservation.domain.model;

/** El pedido de teléfono de la cátedra (I §15.4), reducido a lo que turnos necesita. */
public record PhoneRequestedEvent(String eventId, String reservationProcessId, String holdId) {
}
