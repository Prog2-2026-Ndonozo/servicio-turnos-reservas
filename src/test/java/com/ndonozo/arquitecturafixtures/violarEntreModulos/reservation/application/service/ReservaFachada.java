package com.ndonozo.arquitecturafixtures.violarEntreModulos.reservation.application.service;

import com.ndonozo.arquitecturafixtures.violarEntreModulos.availability.application.service.DisponibilidadFachada;

public class ReservaFachada {
    private final DisponibilidadFachada fachadaAjena = new DisponibilidadFachada();
}
