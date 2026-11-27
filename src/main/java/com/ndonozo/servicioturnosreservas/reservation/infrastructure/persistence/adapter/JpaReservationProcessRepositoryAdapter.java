package com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.adapter;

import com.ndonozo.servicioturnosreservas.reservation.domain.exception.ReservationConflictException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ReservationProcessRepository;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.mapper.ReservationProcessMapper;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaReservationProcessRepositoryAdapter implements ReservationProcessRepository {

    private final JpaReservationProcessRepository repository;
    private final ReservationProcessMapper mapper;

    @Override
    public ReservationProcess save(ReservationProcess process) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(process)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ReservationConflictException("El proceso " + process.reservationProcessId() + " cambió mientras se modificaba", e);
        }
    }

    @Override
    public Optional<ReservationProcess> findByReservationProcessIdAndUserId(String reservationProcessId, long userId) {
        return repository.findByReservationProcessIdAndUserId(reservationProcessId, userId).map(mapper::toDomain);
    }

    @Override
    public Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId) {
        return repository.findByReservationProcessId(reservationProcessId).map(mapper::toDomain);
    }
}
