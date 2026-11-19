package com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto;

// Un campo inválido: solo su nombre y el motivo. Nunca el valor recibido (podría ser una contraseña).
public record FieldViolation(String field, String message) {
}
