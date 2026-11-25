package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.adapter;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmHoldResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.HoldResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.HoldPort;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client.CatedraHoldRejectedException;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client.CatedraHoldRestClient;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.ConfirmHoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.mapper.CatedraHoldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class CatedraHoldAdapter implements HoldPort {

    // I §9 muestra la hora como HH:mm.
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CatedraHoldRestClient client;
    private final CatedraHoldMapper mapper;

    @Override
    public HoldResult createHold(long professionalId, LocalDate date, LocalTime startTime, String externalPatientId) {
        HoldRequest request = new HoldRequest(professionalId, date.toString(), startTime.format(HORA), externalPatientId);
        try {
            return new HoldResult.Created(mapper.toDomain(client.createHold(request)));
        } catch (CatedraHoldRejectedException e) {
            return mapper.toRejection(e.getCode());
        }
    }

    @Override
    public ConfirmHoldResult confirmHold(String holdId, String externalPatientId, String firstName, String lastName) {
        try {
            client.confirmHold(holdId, new ConfirmHoldRequest(externalPatientId, firstName, lastName));
            return new ConfirmHoldResult.Confirmed();
        } catch (CatedraHoldRejectedException e) {
            return mapper.toConfirmRejection(e.getCode());
        }
    }
}
