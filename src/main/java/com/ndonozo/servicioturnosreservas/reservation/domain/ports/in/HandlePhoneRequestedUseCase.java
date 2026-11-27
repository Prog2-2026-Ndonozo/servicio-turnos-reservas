package com.ndonozo.servicioturnosreservas.reservation.domain.ports.in;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestedEvent;

public interface HandlePhoneRequestedUseCase {
    PhoneRequestResult handlePhoneRequested(PhoneRequestedEvent event);
}
