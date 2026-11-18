package com.ndonozo.servicioturnosreservas.User.domain.model;

public sealed interface RegistrationResult {
    record Registered(User user) implements RegistrationResult {}
    record LoginAlreadyUsed() implements RegistrationResult{}
    record EmailAlreadyUsed() implements RegistrationResult{}
}
