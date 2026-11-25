package com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.repository;

import com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaReservationProcessRepository extends JpaRepository<ReservationProcessEntity, Long> {

    Optional<ReservationProcessEntity> findByReservationProcessIdAndUserId(String reservationProcessId, Long userId);
}
