package com.ndonozo.servicioturnosreservas.user.domain.ports.out;

import com.ndonozo.servicioturnosreservas.user.domain.model.User;

import java.util.Optional;

public interface UserRepository {
    User create(User user);
    Optional<User> findByLogin(String login);
    boolean existByLogin(String login);
    boolean existByEmail(String email);
}
