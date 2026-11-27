package com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.mapper;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationProcessMapper {

    public ReservationProcess toDomain(ReservationProcessEntity entity) {
        return ReservationProcess.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .reservationProcessId(entity.getReservationProcessId())
                .holdId(entity.getHoldId())
                .professionalId(entity.getProfessionalId())
                .date(entity.getDate())
                .startTime(entity.getStartTime())
                .externalPatientId(entity.getExternalPatientId())
                .status(entity.getStatus())
                .expiresAt(entity.getExpiresAt())
                .createdDate(entity.getCreatedDate())
                .lastModifiedDate(entity.getLastModifiedDate())
                .phoneRequestEventId(entity.getPhoneRequestEventId())
                .version(entity.getVersion())
                .reservationId(entity.getReservationId())
                .rejectionReason(entity.getRejectionReason())
                .build();
    }

    public ReservationProcessEntity toEntity(ReservationProcess process) {
        ReservationProcessEntity entity = new ReservationProcessEntity();
        entity.setId(process.id());
        entity.setUserId(process.userId());
        entity.setReservationProcessId(process.reservationProcessId());
        entity.setHoldId(process.holdId());
        entity.setProfessionalId(process.professionalId());
        entity.setDate(process.date());
        entity.setStartTime(process.startTime());
        entity.setExternalPatientId(process.externalPatientId());
        entity.setStatus(process.status());
        entity.setExpiresAt(process.expiresAt());
        entity.setCreatedDate(process.createdDate());
        entity.setLastModifiedDate(process.lastModifiedDate());
        entity.setPhoneRequestEventId(process.phoneRequestEventId());
        entity.setVersion(process.version());
        entity.setReservationId(process.reservationId());
        entity.setRejectionReason(process.rejectionReason());
        return entity;
    }
}
