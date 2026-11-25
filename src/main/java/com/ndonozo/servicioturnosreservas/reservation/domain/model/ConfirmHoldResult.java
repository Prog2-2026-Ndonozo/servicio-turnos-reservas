package com.ndonozo.servicioturnosreservas.reservation.domain.model;

/** Respuesta de la cátedra al confirmar un hold. `AlreadyConfirmed` reconcilia un pedido anterior cuya respuesta se perdió. */
public sealed interface ConfirmHoldResult {
    record Confirmed() implements ConfirmHoldResult {}
    record AlreadyConfirmed() implements ConfirmHoldResult {}
    record HoldExpired() implements ConfirmHoldResult {}
    record SlotAlreadyReserved() implements ConfirmHoldResult {}
}
