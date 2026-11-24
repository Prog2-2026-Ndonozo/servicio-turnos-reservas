package com.ndonozo.servicioturnosreservas.availability.domain.model;

import com.ndonozo.servicioturnosreservas.professional.domain.model.WeeklySchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public final class SlotCalculator {
    private SlotCalculator() {}

    public static List<AvailabilitySlot> freeSlots(List<WeeklySchedule> schedules, Collection<Occupancy> occupancies,
                                                   LocalDate from, LocalDate to, LocalDateTime now) {
        Set<LocalDateTime> taken = occupancies.stream()
                .map(occupancy -> occupancy.date().atTime(occupancy.startTime()))
                .collect(Collectors.toSet());
        // Se indexa por el inicio: dos horarios que generan el mismo turno lo ofrecen una sola vez y el orden es el cronológico.
        Map<LocalDateTime, AvailabilitySlot> free = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (WeeklySchedule schedule : schedules) {
                if (schedule.isEnabled() && schedule.getDayOfWeek() == date.getDayOfWeek()) {
                    addSlots(free, schedule, date, taken, now);
                }
            }
        }
        return List.copyOf(free.values());
    }

    private static void addSlots(Map<LocalDateTime, AvailabilitySlot> free, WeeklySchedule schedule, LocalDate date,
                                 Set<LocalDateTime> taken, LocalDateTime now) {
        int minutes = schedule.getSlotDurationMinutes();
        if (minutes <= 0) {
            return;
        }
        LocalTime start = schedule.getStartTime();
        while (true) {
            LocalTime end = start.plusMinutes(minutes);
            // Si sumar la duración pasa la medianoche, end queda antes que start y el turno no entra en el día.
            if (!end.isAfter(start) || end.isAfter(schedule.getEndTime())) {
                return;
            }
            LocalDateTime startAt = date.atTime(start);
            if (startAt.isAfter(now) && !taken.contains(startAt)) {
                free.putIfAbsent(startAt, new AvailabilitySlot(date, start, end));
            }
            start = end;
        }
    }
}