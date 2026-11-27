package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.mapper;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneCommand;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto.ConfirmReservationRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto.CreateReservationRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto.ReservationResponse;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto.SubmitPhoneRequest;
import org.springframework.stereotype.Component;

@Component
public class ReservationDtoMapper {

    public CreateReservationCommand toCommand(long userId, CreateReservationRequest request) {
        return new CreateReservationCommand(userId, request.professionalId(), request.date(), request.startTime());
    }

    public ConfirmReservationCommand toCommand(long userId, String reservationProcessId, ConfirmReservationRequest request) {
        return new ConfirmReservationCommand(userId, reservationProcessId, request.firstName().strip(), request.lastName().strip());
    }

    public SubmitPhoneCommand toCommand(long userId, String reservationProcessId, SubmitPhoneRequest request) {
        return new SubmitPhoneCommand(userId, reservationProcessId, request.phoneNumber().strip());
    }

    // El holdId no sale: es un identificador interno de la cátedra (ADR-0008).
    public ReservationResponse toResponse(ReservationProcess process) {
        return new ReservationResponse(process.reservationProcessId(), process.status().name(), process.expiresAt());
    }
}
