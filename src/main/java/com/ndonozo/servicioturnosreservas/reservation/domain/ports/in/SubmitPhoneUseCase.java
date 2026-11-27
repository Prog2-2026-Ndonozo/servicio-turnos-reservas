package com.ndonozo.servicioturnosreservas.reservation.domain.ports.in;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneResult;

public interface SubmitPhoneUseCase {
    SubmitPhoneResult submitPhone(SubmitPhoneCommand command);
}
