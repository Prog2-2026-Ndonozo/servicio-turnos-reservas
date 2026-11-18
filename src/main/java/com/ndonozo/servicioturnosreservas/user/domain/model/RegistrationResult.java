package com.ndonozo.servicioturnosreservas.user.domain.model;

public sealed interface RegistrationResult {
    record Registered(User user) implements RegistrationResult {}
    record LoginAlreadyUsed() implements RegistrationResult{}
    record EmailAlreadyUsed() implements RegistrationResult{}
}
