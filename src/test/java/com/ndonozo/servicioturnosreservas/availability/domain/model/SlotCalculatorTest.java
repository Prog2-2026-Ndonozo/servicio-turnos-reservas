package com.ndonozo.servicioturnosreservas.availability.domain.model;

import com.ndonozo.servicioturnosreservas.professional.domain.model.WeeklySchedule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El cálculo de turnos libres (ADR-0007): 2026-11-23 es lunes. */
class SlotCalculatorTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime ANTES_DE_TODO = LocalDateTime.of(2026, 1, 1, 0, 0);

    private static WeeklySchedule horario(DayOfWeek dia, String desde, String hasta, int minutos, boolean habilitado) {
        return WeeklySchedule.builder().dayOfWeek(dia).startTime(LocalTime.parse(desde)).endTime(LocalTime.parse(hasta))
                .slotDurationMinutes(minutos).enabled(habilitado).build();
    }

    private static WeeklySchedule lunes(String desde, String hasta, int minutos) {
        return horario(DayOfWeek.MONDAY, desde, hasta, minutos, true);
    }

    private static AvailabilitySlot turno(LocalDate fecha, String desde, String hasta) {
        return new AvailabilitySlot(fecha, LocalTime.parse(desde), LocalTime.parse(hasta));
    }

    private static List<AvailabilitySlot> calcular(List<WeeklySchedule> horarios, List<Occupancy> ocupaciones,
                                                   LocalDate desde, LocalDate hasta, LocalDateTime ahora) {
        return SlotCalculator.freeSlots(horarios, ocupaciones, desde, hasta, ahora);
    }

    @Test
    void sin_horarios_no_hay_turnos() {
        assertThat(calcular(List.of(), List.of(), LUNES, LUNES.plusDays(6), ANTES_DE_TODO)).isEmpty();
    }

    @Test
    void un_horario_se_divide_en_turnos_de_la_duracion_indicada() {
        assertThat(calcular(List.of(lunes("09:00", "10:30", 30)), List.of(), LUNES, LUNES, ANTES_DE_TODO))
                .containsExactly(turno(LUNES, "09:00", "09:30"), turno(LUNES, "09:30", "10:00"), turno(LUNES, "10:00", "10:30"));
    }

    @Test
    void un_turno_parcial_no_se_ofrece() {
        assertThat(calcular(List.of(lunes("09:00", "10:00", 45)), List.of(), LUNES, LUNES, ANTES_DE_TODO))
                .containsExactly(turno(LUNES, "09:00", "09:45"));
    }

    @Test
    void un_horario_mas_corto_que_el_turno_no_ofrece_nada() {
        assertThat(calcular(List.of(lunes("09:00", "09:30", 60)), List.of(), LUNES, LUNES, ANTES_DE_TODO)).isEmpty();
    }

    @Test
    void un_turno_confirmado_o_retenido_se_descuenta_por_su_hora_de_inicio() {
        List<Occupancy> ocupaciones = List.of(new Occupancy(LUNES, LocalTime.of(9, 0)), new Occupancy(LUNES, LocalTime.of(10, 0)));

        assertThat(calcular(List.of(lunes("09:00", "10:30", 30)), ocupaciones, LUNES, LUNES, ANTES_DE_TODO))
                .containsExactly(turno(LUNES, "09:30", "10:00"));
    }

    @Test
    void una_ocupacion_de_otro_dia_o_de_otra_hora_no_bloquea() {
        List<Occupancy> ocupaciones = List.of(new Occupancy(LUNES.plusDays(7), LocalTime.of(9, 0)),
                new Occupancy(LUNES, LocalTime.of(9, 10)));

        assertThat(calcular(List.of(lunes("09:00", "10:00", 30)), ocupaciones, LUNES, LUNES, ANTES_DE_TODO)).hasSize(2);
    }

    @Test
    void los_horarios_deshabilitados_se_ignoran() {
        List<WeeklySchedule> horarios = List.of(horario(DayOfWeek.MONDAY, "09:00", "10:00", 30, false));

        assertThat(calcular(horarios, List.of(), LUNES, LUNES, ANTES_DE_TODO)).isEmpty();
    }

    @Test
    void solo_se_usan_los_horarios_del_dia_de_la_semana_de_cada_fecha() {
        List<WeeklySchedule> horarios = List.of(lunes("09:00", "09:30", 30), horario(DayOfWeek.WEDNESDAY, "10:00", "10:30", 30, true));

        assertThat(calcular(horarios, List.of(), LUNES, LUNES.plusDays(13), ANTES_DE_TODO))
                .containsExactly(turno(LUNES, "09:00", "09:30"), turno(LUNES.plusDays(2), "10:00", "10:30"),
                        turno(LUNES.plusDays(7), "09:00", "09:30"), turno(LUNES.plusDays(9), "10:00", "10:30"));
    }

    @Test
    void el_rango_incluye_el_primer_y_el_ultimo_dia() {
        List<WeeklySchedule> horarios = List.of(horario(DayOfWeek.SUNDAY, "09:00", "09:30", 30, true), lunes("09:00", "09:30", 30));

        assertThat(calcular(horarios, List.of(), LUNES.minusDays(1), LUNES, ANTES_DE_TODO)).hasSize(2);
    }

    @Test
    void los_turnos_salen_en_orden_aunque_los_horarios_vengan_desordenados() {
        List<WeeklySchedule> horarios = List.of(lunes("15:00", "15:30", 30), lunes("09:00", "09:30", 30));

        assertThat(calcular(horarios, List.of(), LUNES, LUNES, ANTES_DE_TODO))
                .containsExactly(turno(LUNES, "09:00", "09:30"), turno(LUNES, "15:00", "15:30"));
    }

    @Test
    void dos_horarios_que_generan_el_mismo_turno_lo_ofrecen_una_sola_vez() {
        List<WeeklySchedule> horarios = List.of(lunes("09:00", "10:00", 30), lunes("09:30", "10:30", 30));

        assertThat(calcular(horarios, List.of(), LUNES, LUNES, ANTES_DE_TODO))
                .containsExactly(turno(LUNES, "09:00", "09:30"), turno(LUNES, "09:30", "10:00"), turno(LUNES, "10:00", "10:30"));
    }

    @Test
    void un_turno_que_ya_empezo_no_se_ofrece_ni_el_que_empieza_justo_ahora() {
        LocalDateTime ahora = LocalDateTime.of(LUNES, LocalTime.of(9, 30));

        assertThat(calcular(List.of(lunes("09:00", "10:30", 30)), List.of(), LUNES, LUNES, ahora))
                .containsExactly(turno(LUNES, "10:00", "10:30"));
    }

    @Test
    void los_dias_anteriores_a_hoy_no_ofrecen_nada() {
        LocalDateTime ahora = LocalDateTime.of(LUNES.plusDays(1), LocalTime.of(8, 0));

        assertThat(calcular(List.of(lunes("09:00", "10:00", 30)), List.of(), LUNES, LUNES.plusDays(7), ahora))
                .containsExactly(turno(LUNES.plusDays(7), "09:00", "09:30"), turno(LUNES.plusDays(7), "09:30", "10:00"));
    }

    @Test
    void una_duracion_invalida_no_cuelga_el_calculo() {
        List<WeeklySchedule> horarios = List.of(lunes("09:00", "10:00", 0), lunes("09:00", "10:00", -30));

        assertThat(calcular(horarios, List.of(), LUNES, LUNES, ANTES_DE_TODO)).isEmpty();
    }

    @Test
    void un_horario_que_llega_a_la_medianoche_no_da_vueltas_al_dia() {
        assertThat(calcular(List.of(lunes("22:00", "23:59", 120)), List.of(), LUNES, LUNES, ANTES_DE_TODO)).isEmpty();
        assertThat(calcular(List.of(lunes("22:00", "23:59", 60)), List.of(), LUNES, LUNES, ANTES_DE_TODO))
                .containsExactly(turno(LUNES, "22:00", "23:00"));
    }
}
