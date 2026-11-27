package com.ndonozo.servicioturnosreservas.reservation.domain.ports.out;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneSubmission;

public interface PhoneSubmissionPort {

    /**
     * Publica el teléfono y espera la confirmación del broker. Falla con una excepción si no se pudo publicar: el
     * llamador decide cómo deshacer su estado.
     */
    void submit(PhoneSubmission submission);
}
