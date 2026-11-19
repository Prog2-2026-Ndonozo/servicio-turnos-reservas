package com.ndonozo.servicioturnosreservas.user.application.usecases;

import com.ndonozo.servicioturnosreservas.user.domain.model.AuthToken;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.PasswordHasher;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.TokenIssuer;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenIssuer tokenIssuer;
    @InjectMocks
    private AuthenticateUserUseCaseImpl useCase;

    private User usuario(boolean activo) {
        return User.builder().id(7L).login("ana").passwordHash("hash").activated(activo).build();
    }

    @Test
    void con_credenciales_correctas_devuelve_el_token_emitido() {
        User user = usuario(true);
        when(userRepository.findByLogin("ana")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("secreto", "hash")).thenReturn(true);
        when(tokenIssuer.issue(user)).thenReturn(new AuthToken("jwt"));

        assertThat(useCase.authenticateUser("ana", "secreto")).contains(new AuthToken("jwt"));
    }

    @Test
    void busca_el_login_en_minusculas() {
        User user = usuario(true);
        when(userRepository.findByLogin("ana")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("secreto", "hash")).thenReturn(true);
        when(tokenIssuer.issue(user)).thenReturn(new AuthToken("jwt"));

        assertThat(useCase.authenticateUser("ANA", "secreto")).isPresent();
    }

    @Test
    void con_un_usuario_inexistente_devuelve_vacio() {
        when(userRepository.findByLogin("ana")).thenReturn(Optional.empty());

        assertThat(useCase.authenticateUser("ana", "secreto")).isEmpty();
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void con_la_contrasena_incorrecta_devuelve_vacio_sin_emitir_token() {
        when(userRepository.findByLogin("ana")).thenReturn(Optional.of(usuario(true)));
        when(passwordHasher.matches("mala", "hash")).thenReturn(false);

        assertThat(useCase.authenticateUser("ana", "mala")).isEmpty();
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void con_un_usuario_inactivo_devuelve_vacio_sin_comprobar_la_contrasena() {
        when(userRepository.findByLogin("ana")).thenReturn(Optional.of(usuario(false)));

        assertThat(useCase.authenticateUser("ana", "secreto")).isEmpty();
        verify(passwordHasher, never()).matches(any(), any());
    }
}
