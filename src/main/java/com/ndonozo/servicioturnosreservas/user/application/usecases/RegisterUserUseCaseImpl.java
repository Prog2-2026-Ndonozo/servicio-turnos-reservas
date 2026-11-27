package com.ndonozo.servicioturnosreservas.user.application.usecases;

import com.ndonozo.servicioturnosreservas.user.domain.model.RegisterUserCommand;
import com.ndonozo.servicioturnosreservas.user.domain.model.RegistrationResult;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.domain.ports.in.RegisterUserUseCase;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.PasswordHasher;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Registro del usuario final: valida que login y email estén libres, arma el usuario y lo guarda.
 * Devuelve un RegistrationResult; no lanza excepciones por casos de negocio.
 */
@Component
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    // Puertos de salida: base de usuarios y algoritmo de hash.
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Override
    public RegistrationResult registerUser(RegisterUserCommand command){
        // 1. Login y email se normalizan a minúsculas (la base lo exige con un CHECK).
        String login = command.login().toLowerCase(Locale.ROOT);
        String email = command.email().toLowerCase(Locale.ROOT);

        // 2. Si ya existen, se corta con el resultado correspondiente (la fachada lo traduce a 409).
        if(userRepository.existByLogin(login)){
            return new RegistrationResult.LoginAlreadyUsed();
        }
        if(userRepository.existByEmail(email)){
            return new RegistrationResult.EmailAlreadyUsed();
        }

        Instant now = Instant.now();

        // 3. Se arma el usuario. La contraseña se guarda hasheada, queda activo sin verificar el correo y recibe
        //    la autoridad ROLE_USER (se guarda en user_authority): el cliente no elige ni id, ni estado, ni roles.
        User user= User.builder().login(login)
                .passwordHash(passwordHasher.hash(command.password()))
                .firstName(command.firstName())
                .lastName(command.lastName())
                .email(email)
                .imageUrl(command.imageUrl())
                .activated(true)
                .langKey(command.langKey())
                .authorities(Set.of(User.ROLE_USER))
                .createdDate(now)
                .lastModifiedDate(now)
                .build();

        // 4. Se guarda y se devuelve el usuario creado.
        return new RegistrationResult.Registered(userRepository.create(user));
    }
}
