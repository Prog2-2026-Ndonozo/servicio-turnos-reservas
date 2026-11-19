package com.ndonozo.servicioturnosreservas.user.infrastructure.web.controller;

import com.ndonozo.servicioturnosreservas.user.application.service.UserService;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto.AuthenticateRequest;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto.RegisterRequest;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto.TokenResponse;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.mapper.UserDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Registro e inicio de sesion del usuario final")
public class UserController {

    private final UserService userService;
    private final UserDtoMapper mapper;

    @Operation(summary = "Registro de usuario" )

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request){

        userService.register(mapper.toCommand(request));
    }
    @Operation( summary = "Inicia sesion y devuelve un JWT en id_token")
    @PostMapping("/authenticate")
    public TokenResponse authenticate(@Valid @RequestBody AuthenticateRequest request){
        return mapper.toResponse(userService.authenticate(request.username(),request.password()));
    }
}
