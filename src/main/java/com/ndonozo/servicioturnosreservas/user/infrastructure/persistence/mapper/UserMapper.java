package com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.mapper;

import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class UserMapper {

    public User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        return User.builder()
                .id(entity.getId())
                .login(entity.getLogin())
                .passwordHash(entity.getPasswordHash())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .imageUrl(entity.getImageUrl())
                .activated(entity.isActivated())
                .langKey(entity.getLangKey())
                .authorities(new HashSet<>(entity.getAuthorities()))
                .createdDate(entity.getCreatedDate())
                .lastModifiedDate(entity.getLastModifiedDate())
                .build();
    }

    public UserEntity toEntity(User user) {
        if (user == null) {
            return null;
        }
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setLogin(user.getLogin());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setFirstName(user.getFirstName());
        entity.setLastName(user.getLastName());
        entity.setEmail(user.getEmail());
        entity.setImageUrl(user.getImageUrl());
        entity.setActivated(user.isActivated());
        entity.setLangKey(user.getLangKey());
        entity.setAuthorities(new HashSet<>(user.getAuthorities()));
        entity.setCreatedDate(user.getCreatedDate());
        entity.setLastModifiedDate(user.getLastModifiedDate());
        return entity;
    }
}