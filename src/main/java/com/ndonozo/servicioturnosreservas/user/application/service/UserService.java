package com.ndonozo.servicioturnosreservas.user.application.service;

import com.ndonozo.servicioturnosreservas.user.application.exception.EmailAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.application.exception.InvalidCredentialsException;
import com.ndonozo.servicioturnosreservas.user.application.exception.LoginAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.domain.model.AuthToken;
import com.ndonozo.servicioturnosreservas.user.domain.model.RegisterUserCommand;
import com.ndonozo.servicioturnosreservas.user.domain.model.RegistrationResult;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.domain.ports.in.AuthenticateUserUseCase;
import com.ndonozo.servicioturnosreservas.user.domain.ports.in.RegisterUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;

    public User register(RegisterUserCommand command){
        return switch(registerUser.registerUser(command)){
        case RegistrationResult.Registered registered -> registered.user();
        case RegistrationResult.LoginAlreadyUsed ignored -> throw new LoginAlreadyUsedException("El login ya esta en uso");
        case RegistrationResult.EmailAlreadyUsed ignored -> throw new EmailAlreadyUsedException("El mail ya esta en uso");
        };

    }
    public AuthToken authenticate(String login, String password){
        return authenticateUser.authenticateUser(login, password).orElseThrow(()-> new InvalidCredentialsException("Credenciales invalidas"));
    }

}
