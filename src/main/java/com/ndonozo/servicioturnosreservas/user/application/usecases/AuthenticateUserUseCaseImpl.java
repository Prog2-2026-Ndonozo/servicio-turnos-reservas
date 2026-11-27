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

/**
 * Login del usuario final: si las credenciales son correctas, devuelve el JWT.
 * Es una cadena de condiciones sobre un Optional: si cualquiera falla, el resultado queda vacío.
 */
@Component
@RequiredArgsConstructor
public class AuthenticateUserUseCaseImpl implements AuthenticateUserUseCase {

    // Puertos de salida: base de usuarios, algoritmo de hash y emisor del JWT. El caso de uso no sabe cuáles son.
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    @Override

    public Optional<AuthToken> authenticateUser(String login, String password){
        // 1. Busca el usuario por login en minúsculas (los logins se guardan así).
        // 2. Filtra que esté activado.
        // 3. Filtra que la contraseña coincida con el hash guardado (nunca se compara en texto plano).
        // 4. Si todo se cumple, emite el JWT.
        return userRepository.findByLogin(login.toLowerCase(Locale.ROOT)).filter(User::isActivated).filter(user -> passwordHasher.matches(password,user.getPasswordHash()))
                .map(tokenIssuer::issue);
    }





}
