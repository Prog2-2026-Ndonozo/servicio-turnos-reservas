package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.mapper;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.Hold;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.HoldResult;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldResponse;
import org.springframework.stereotype.Component;

@Component
public class CatedraHoldMapper {
    public Hold toDomain(HoldResponse response) {
        return new Hold(response.holdId(), response.reservationProcessId(), response.expiresAt());
    }

    public HoldResult toRejection(String code) {
        return switch (code) {
            case "SLOT_ALREADY_HELD" -> new HoldResult.SlotAlreadyHeld();
            case "SLOT_ALREADY_RESERVED" -> new HoldResult.SlotAlreadyReserved();
            case "INVALID_SLOT" -> new HoldResult.InvalidSlot();
            case "PROFESSIONAL_DISABLED" -> new HoldResult.ProfessionalDisabled();
            case "PROFESSIONAL_NOT_FOUND" -> new HoldResult.ProfessionalNotFound();
            default -> throw new CatedraIntegrationException("La cátedra rechazó el hold con un código desconocido: " + code);
        };
    }
}
