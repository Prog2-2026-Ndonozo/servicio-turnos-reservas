package com.ndonozo.servicioturnosreservas.User.domain.model;

public record RegisterUserCommand(
        String login,
        String password,
        String firstName,
        String lastName,
        String email,
        String imageUrl,
        String langKey

) {
}

// datos que el usuario elige al registrarse. la contraseña viaja en claro hasta el caso de uso, que la hashea
