package com.ndonozo.servicioturnosreservas.availability.infrastructure.web.mapper;

import com.ndonozo.servicioturnosreservas.availability.domain.model.Availability;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.web.dto.AvailabilityResponse;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.web.dto.SlotResponse;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityDtoMapper {
    public AvailabilityResponse toResponse(Availability availability){
        return new AvailabilityResponse(availability.professionalId(),availability.from(),availability.to(),availability.slots().stream()
                .map(slot-> new SlotResponse(slot.date(),slot.startTime(),slot.endTime())).toList());
    }
}
