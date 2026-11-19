package com.ndonozo.servicioturnosreservas.user.infrastructure.security;

import com.ndonozo.servicioturnosreservas.user.domain.ports.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword){
        return encoder.encode(rawPassword);

    }

    @Override
    public boolean matches(String rawPassword, String passwordHash){
        return encoder.matches(rawPassword, passwordHash);
    }


}
