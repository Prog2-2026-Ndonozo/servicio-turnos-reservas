package com.ndonozo.servicioturnosreservas.user.domain.ports.in;

import com.ndonozo.servicioturnosreservas.user.domain.model.AuthToken;

import java.util.Optional;

public interface AuthenticateUserUseCase {

    Optional<AuthToken> authenticateUser(String login, String password);
}
