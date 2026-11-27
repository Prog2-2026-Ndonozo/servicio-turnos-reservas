package com.ndonozo.servicioturnosreservas.reservation.domain.model;

public sealed interface PhoneRequestResult {
    /** El proceso pasó a esperar el teléfono, o ya lo esperaba, y quedó guardado el eventId del pedido. */
    record Applied(ReservationProcess process) implements PhoneRequestResult {}
    /** El eventId ya se había procesado: no se repite ningún efecto (I §18.1). */
    record Duplicate() implements PhoneRequestResult {}
    record ProcessNotFound() implements PhoneRequestResult {}
    record HoldMismatch() implements PhoneRequestResult {}
    /** El proceso ya no puede esperar el teléfono (otro estado): el evento no cambia nada. */
    record NotApplicable() implements PhoneRequestResult {}
}
