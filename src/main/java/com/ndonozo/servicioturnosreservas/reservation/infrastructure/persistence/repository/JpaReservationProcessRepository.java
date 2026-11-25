package com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.repository;

import com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReservationProcessRepository extends JpaRepository<ReservationProcessEntity, Long> {
}
