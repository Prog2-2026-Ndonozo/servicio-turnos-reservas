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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private RegisterUserUseCase registerUser;
    @Mock
    private AuthenticateUserUseCase authenticateUser;
    @InjectMocks
    private UserService service;

    private final RegisterUserCommand command = new RegisterUserCommand(
            "ana", "secreto", "Ana", "Torres", "ana@mail.com", null, "es");

    @Test
    void register_devuelve_el_usuario_registrado() {
        User user = User.builder().id(1L).login("ana").build();
        when(registerUser.registerUser(command)).thenReturn(new RegistrationResult.Registered(user));

        assertThat(service.register(command)).isSameAs(user);
    }

    @Test
    void register_traduce_login_repetido_a_LoginAlreadyUsedException() {
        when(registerUser.registerUser(command)).thenReturn(new RegistrationResult.LoginAlreadyUsed());

        assertThatThrownBy(() -> service.register(command)).isInstanceOf(LoginAlreadyUsedException.class);
    }

    @Test
    void register_traduce_email_repetido_a_EmailAlreadyUsedException() {
        when(registerUser.registerUser(command)).thenReturn(new RegistrationResult.EmailAlreadyUsed());

        assertThatThrownBy(() -> service.register(command)).isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void authenticate_devuelve_el_token() {
        when(authenticateUser.authenticateUser("ana", "secreto")).thenReturn(Optional.of(new AuthToken("jwt")));

        assertThat(service.authenticate("ana", "secreto")).isEqualTo(new AuthToken("jwt"));
    }

    @Test
    void authenticate_sin_resultado_lanza_InvalidCredentialsException() {
        when(authenticateUser.authenticateUser("ana", "mala")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("ana", "mala")).isInstanceOf(InvalidCredentialsException.class);
    }
}
