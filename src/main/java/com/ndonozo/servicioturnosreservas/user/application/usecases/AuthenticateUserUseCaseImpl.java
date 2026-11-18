package com.ndonozo.servicioturnosreservas.user.application.usecases;

import com.ndonozo.servicioturnosreservas.user.domain.model.AuthToken;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.domain.ports.in.AuthenticateUserUseCase;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.PasswordHasher;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.TokenIssuer;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthenticateUserUseCaseImpl implements AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    @Override

    public Optional<AuthToken> authenticateUser(String login, String password){
        return userRepository.findByLogin(login.toLowerCase(Locale.ROOT)).filter(User::isActivated).filter(user -> passwordHasher.matches(password,user.getPasswordHash()))
                .map(tokenIssuer::issue);
    }





}
