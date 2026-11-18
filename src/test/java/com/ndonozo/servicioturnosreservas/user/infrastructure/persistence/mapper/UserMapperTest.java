package com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.mapper;

import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private static final Instant CREADO = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant MODIFICADO = Instant.parse("2026-11-18T13:00:00Z");

    private final UserMapper mapper = new UserMapper();

    @Test
    void mapea_todos_los_campos_hacia_la_entidad() {
        User user = User.builder().id(7L).login("ana").passwordHash("hash").firstName("Ana").lastName("Paz")
                .email("ana@correo.com").imageUrl("http://img").activated(true).langKey("es")
                .authorities(Set.of(User.ROLE_USER)).createdDate(CREADO).lastModifiedDate(MODIFICADO).build();

        UserEntity entity = mapper.toEntity(user);

        assertThat(entity.getId()).isEqualTo(7L);
        assertThat(entity.getLogin()).isEqualTo("ana");
        assertThat(entity.getPasswordHash()).isEqualTo("hash");
        assertThat(entity.getFirstName()).isEqualTo("Ana");
        assertThat(entity.getLastName()).isEqualTo("Paz");
        assertThat(entity.getEmail()).isEqualTo("ana@correo.com");
        assertThat(entity.getImageUrl()).isEqualTo("http://img");
        assertThat(entity.isActivated()).isTrue();
        assertThat(entity.getLangKey()).isEqualTo("es");
        assertThat(entity.getAuthorities()).containsExactly(User.ROLE_USER);
        assertThat(entity.getCreatedDate()).isEqualTo(CREADO);
        assertThat(entity.getLastModifiedDate()).isEqualTo(MODIFICADO);
    }

    @Test
    void mapea_todos_los_campos_hacia_el_dominio() {
        UserEntity entity = new UserEntity();
        entity.setId(7L);
        entity.setLogin("ana");
        entity.setPasswordHash("hash");
        entity.setFirstName("Ana");
        entity.setLastName("Paz");
        entity.setEmail("ana@correo.com");
        entity.setImageUrl("http://img");
        entity.setActivated(true);
        entity.setLangKey("es");
        entity.setAuthorities(Set.of(User.ROLE_USER));
        entity.setCreatedDate(CREADO);
        entity.setLastModifiedDate(MODIFICADO);

        User user = mapper.toDomain(entity);

        assertThat(user).usingRecursiveComparison().isEqualTo(User.builder().id(7L).login("ana")
                .passwordHash("hash").firstName("Ana").lastName("Paz").email("ana@correo.com")
                .imageUrl("http://img").activated(true).langKey("es").authorities(Set.of(User.ROLE_USER))
                .createdDate(CREADO).lastModifiedDate(MODIFICADO).build());
    }

    @Test
    void devuelve_null_ante_null() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
