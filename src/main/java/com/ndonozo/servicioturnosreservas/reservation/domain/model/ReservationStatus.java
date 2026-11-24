package com.ndonozo.servicioturnosreservas.reservation.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum ReservationStatus {
    HELD, WAITING_FOR_PHONE, PHONE_SUBMITTED, CONFIRMED, CANCELLED, EXPIRED, FAILED;

    public boolean canTransitionTo(ReservationStatus target) {
        return allowedTargets().contains(target);
    }

    /** Un estado final no admite ninguna transición: un evento tardío no lo puede alterar (E §7). */
    public boolean isFinal() {
        return allowedTargets().isEmpty();
    }

    private Set<ReservationStatus> allowedTargets() {
        return switch (this) {
            case HELD -> EnumSet.of(WAITING_FOR_PHONE, EXPIRED, FAILED);
            case WAITING_FOR_PHONE -> EnumSet.of(PHONE_SUBMITTED, EXPIRED, FAILED);
            // El rechazo del teléfono es reintentable hasta expiresAt: vuelve a pedirlo.
            case PHONE_SUBMITTED -> EnumSet.of(CONFIRMED, WAITING_FOR_PHONE, EXPIRED, FAILED);
            // Una reserva confirmada solo se puede cancelar: no vence ni falla por un evento tardío.
            case CONFIRMED -> EnumSet.of(CANCELLED);
            case CANCELLED, EXPIRED, FAILED -> EnumSet.noneOf(ReservationStatus.class);
        };
    }
}