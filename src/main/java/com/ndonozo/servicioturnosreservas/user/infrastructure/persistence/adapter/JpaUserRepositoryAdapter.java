package com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.adapter;

import com.ndonozo.servicioturnosreservas.user.application.exception.EmailAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.application.exception.LoginAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.UserRepository;
import com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.mapper.UserMapper;
import com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.hibernate.exception.ConstraintViolationException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepository {private static final String UNIQUE_LOGIN = "uk_app_user_login";
    private static final String UNIQUE_EMAIL = "uk_app_user_email";

    private final JpaUserRepository repository;
    private final UserMapper mapper;

    @Override
    public User create(User user) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            throw traducir(e);
        }
    }

    @Override
    public Optional<User> findByLogin(String login) {
        return repository.findByLogin(login).map(mapper::toDomain);
    }

    @Override
    public boolean existByLogin(String login) {
        return repository.existsByLogin(login);
    }

    @Override
    public boolean existByEmail(String email) {
        return repository.existsByEmail(email);
    }

    // Dos registros simultáneos pueden pasar el chequeo previo; la restricción UNIQUE de la base es la defensa real.
    private RuntimeException traducir(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException violation) {
            String constraint = String.valueOf(violation.getConstraintName());
            if (constraint.contains(UNIQUE_LOGIN)) {
                return new LoginAlreadyUsedException("El login ya está en uso");
            }
            if (constraint.contains(UNIQUE_EMAIL)) {
                return new EmailAlreadyUsedException("El email ya está en uso");
            }
        }
        return e;
    }
}
