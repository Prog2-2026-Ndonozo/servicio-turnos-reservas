package com.ndonozo.servicioturnosreservas.user.domain.ports.out;

public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String passwordHash);

}
