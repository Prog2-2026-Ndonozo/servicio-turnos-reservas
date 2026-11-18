package com.ndonozo.servicioturnosreservas.user.domain.ports.in;

import com.ndonozo.servicioturnosreservas.user.domain.model.RegisterUserCommand;
import com.ndonozo.servicioturnosreservas.user.domain.model.RegistrationResult;

public interface RegisterUserUseCase {
    RegistrationResult registerUser(RegisterUserCommand command);
}
