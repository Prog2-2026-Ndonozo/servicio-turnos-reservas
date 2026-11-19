package com.ndonozo.servicioturnosreservas.user.application.usecases;

import com.ndonozo.servicioturnosreservas.user.domain.model.RegisterUserCommand;
import com.ndonozo.servicioturnosreservas.user.domain.model.RegistrationResult;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.PasswordHasher;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @InjectMocks
    private RegisterUserUseCaseImpl useCase;

    private final RegisterUserCommand command = new RegisterUserCommand(
            "Ana_Torres", "secreto", "Ana", "Torres", "Ana@Mail.COM", null, "es");

    @Test
    void registrar_guarda_un_usuario_activo_con_ROLE_USER_y_la_contrasena_hasheada() {
        when(passwordHasher.hash("secreto")).thenReturn("hash-bcrypt");
        when(userRepository.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrationResult result = useCase.registerUser(command);

        ArgumentCaptor<User> guardado = ArgumentCaptor.forClass(User.class);
        verify(userRepository).create(guardado.capture());
        User user = guardado.getValue();
        assertThat(result).isEqualTo(new RegistrationResult.Registered(user));
        assertThat(user.getPasswordHash()).isEqualTo("hash-bcrypt");
        assertThat(user.isActivated()).isTrue();
        assertThat(user.getAuthorities()).containsExactly(User.ROLE_USER);
        assertThat(user.getCreatedDate()).isNotNull();
    }

    @Test
    void registrar_guarda_login_y_email_en_minusculas() {
        when(passwordHasher.hash("secreto")).thenReturn("hash-bcrypt");
        when(userRepository.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.registerUser(command);

        ArgumentCaptor<User> guardado = ArgumentCaptor.forClass(User.class);
        verify(userRepository).create(guardado.capture());
        assertThat(guardado.getValue().getLogin()).isEqualTo("ana_torres");
        assertThat(guardado.getValue().getEmail()).isEqualTo("ana@mail.com");
    }

    @Test
    void registrar_con_un_login_repetido_informa_LoginAlreadyUsed_sin_guardar() {
        when(userRepository.existByLogin("ana_torres")).thenReturn(true);

        RegistrationResult result = useCase.registerUser(command);

        assertThat(result).isInstanceOf(RegistrationResult.LoginAlreadyUsed.class);
        verify(userRepository, never()).create(any());
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void registrar_con_un_email_repetido_informa_EmailAlreadyUsed_sin_guardar() {
        when(userRepository.existByEmail("ana@mail.com")).thenReturn(true);

        RegistrationResult result = useCase.registerUser(command);

        assertThat(result).isInstanceOf(RegistrationResult.EmailAlreadyUsed.class);
        verify(userRepository, never()).create(any());
    }
}
