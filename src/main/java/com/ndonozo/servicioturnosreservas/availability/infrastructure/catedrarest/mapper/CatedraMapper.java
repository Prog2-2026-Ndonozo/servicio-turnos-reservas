package com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.mapper;

import com.ndonozo.servicioturnosreservas.availability.domain.model.Occupancy;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.dto.OccupanciesResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CatedraMapper {
    // Confirmed y HELD bloquean por igual: el estado no hace falta para calcular la disponibilidad
    public List<Occupancy> toDomain(OccupanciesResponse response){
        return response.occupancies().stream().map(item -> new Occupancy(item.date(),item.startTime())).toList();
    }

}
