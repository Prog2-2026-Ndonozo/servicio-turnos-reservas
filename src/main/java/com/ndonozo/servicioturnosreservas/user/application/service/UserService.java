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

/**
 * Fachada del módulo user: es lo único que llama el controller. Tiene un método por caso de uso y traduce el
 * resultado de cada uno a excepciones de aplicación, que el @RestControllerAdvice convierte en problem+json.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    // Puertos de entrada: se llama a las interfaces, Spring inyecta las clases ...UseCaseImpl.
    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;

    // Registro: el caso de uso devuelve un RegistrationResult y acá cada desenlace se traduce.
    public User register(RegisterUserCommand command){
        return switch(registerUser.registerUser(command)){
        case RegistrationResult.Registered registered -> registered.user();
        case RegistrationResult.LoginAlreadyUsed ignored -> throw new LoginAlreadyUsedException("El login ya esta en uso");
        case RegistrationResult.EmailAlreadyUsed ignored -> throw new EmailAlreadyUsedException("El mail ya esta en uso");
        };

    }
    // Login: el caso de uso devuelve un Optional. Vacío = credenciales inválidas (401), sin distinguir si falló el
    // usuario o la contraseña, para no revelar qué usuarios existen.
    public AuthToken authenticate(String login, String password){
        return authenticateUser.authenticateUser(login, password).orElseThrow(()-> new InvalidCredentialsException("Credenciales invalidas"));
    }

}
