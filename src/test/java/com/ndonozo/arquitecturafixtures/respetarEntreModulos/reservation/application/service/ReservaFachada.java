package com.ndonozo.arquitecturafixtures.respetarEntreModulos.reservation.application.service;

import com.ndonozo.arquitecturafixtures.respetarEntreModulos.availability.domain.model.Slot;
import com.ndonozo.arquitecturafixtures.respetarEntreModulos.availability.domain.ports.in.ConsultarSlotsUseCase;

public class ReservaFachada {
    private ConsultarSlotsUseCase slots;
    private Slot ultimo;
}
