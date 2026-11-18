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

@Component
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Override
    public RegistrationResult registerUser(RegisterUserCommand command){
        String login = command.login().toLowerCase(Locale.ROOT);
        String email = command.email().toLowerCase(Locale.ROOT);

        if(userRepository.existByLogin(login)){
            return new RegistrationResult.LoginAlreadyUsed();
        }
        if(userRepository.existByEmail(email)){
            return new RegistrationResult.EmailAlreadyUsed();
        }

        Instant now = Instant.now();

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

        return new RegistrationResult.Registered(userRepository.create(user));
    }
}
