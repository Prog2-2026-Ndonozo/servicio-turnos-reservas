package com.ndonozo.servicioturnosreservas.user.infrastructure.web.mapper;

import com.ndonozo.servicioturnosreservas.user.domain.model.AuthToken;
import com.ndonozo.servicioturnosreservas.user.domain.model.RegisterUserCommand;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto.RegisterRequest;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto.TokenResponse;
import org.springframework.stereotype.Component;

@Component
public class UserDtoMapper {

    public RegisterUserCommand toCommand(RegisterRequest request) {
        return new RegisterUserCommand(request.login(), request.password(), request.firstName(),
                request.lastName(), request.email(), request.imageUrl(), request.langKey());
    }

    public TokenResponse toResponse(AuthToken token) {
        return new TokenResponse(token.value());
    }
}