package com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.entity;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reservation_process")
@NoArgsConstructor
@Getter
@Setter
public class ReservationProcessEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Solo el id del usuario: la entidad de user es de otro módulo y no se importa.
    private Long userId;
    private String reservationProcessId;
    private String holdId;
    private Long professionalId;

    @Column(name = "slot_date")
    private LocalDate date;

    @Column(name = "slot_start_time")
    private LocalTime startTime;

    private String externalPatientId;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private Instant expiresAt;
    private Instant createdDate;
    private Instant lastModifiedDate;

    private String phoneRequestEventId;

    // Bloqueo optimista: dos escritores del mismo proceso no se pisan (ADR-0010).
    @Version
    private Long version;
}