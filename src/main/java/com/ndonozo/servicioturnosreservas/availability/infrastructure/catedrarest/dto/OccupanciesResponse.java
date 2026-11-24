package com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OccupanciesResponse(
        Long professionalId,
        LocalDate from,
        LocalDate to,
        List<Item> occupancies)
{
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(LocalDate date, LocalTime startTime, String status) {}
}