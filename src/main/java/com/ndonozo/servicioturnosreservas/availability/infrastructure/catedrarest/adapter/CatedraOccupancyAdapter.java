package com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.adapter;

import com.ndonozo.servicioturnosreservas.availability.domain.model.Occupancy;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.out.OccupancyPort;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.client.CatedraRestClient;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.mapper.CatedraMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CatedraOccupancyAdapter implements OccupancyPort {
    private final CatedraRestClient client ;
    private final CatedraMapper mapper;

    @Override
    public List<Occupancy> findOccupancies(long professionalId, LocalDate from, LocalDate to){
        return mapper.toDomain(client.findOccupancies(professionalId,from,to));
    }
}
