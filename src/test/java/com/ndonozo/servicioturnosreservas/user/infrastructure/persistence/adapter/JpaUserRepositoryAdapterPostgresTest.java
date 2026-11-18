package com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.adapter;

import com.ndonozo.servicioturnosreservas.user.application.exception.EmailAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.application.exception.LoginAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.infrastructure.persistence.mapper.UserMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba contra un PostgreSQL real (misma imagen que el Compose). Flyway aplica V1 y Hibernate corre en
 * ddl-auto=validate: tambien comprueba que la entidad calza con las tablas. Cada test se revierte al terminar.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({JpaUserRepositoryAdapter.class, UserMapper.class})
class JpaUserRepositoryAdapterPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final Instant FECHA = Instant.parse("2026-11-18T12:00:00Z");

    @Autowired
    private JpaUserRepositoryAdapter adapter;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void create_asigna_el_id_y_guarda_las_autoridades() {
        User creado = adapter.create(usuario("ana", "ana@correo.com"));

        assertThat(creado.getId()).isNotNull();
        List<String> autoridades = jdbc.queryForList(
                "SELECT authority FROM user_authority WHERE user_id = ?", String.class, creado.getId());
        assertThat(autoridades).containsExactly(User.ROLE_USER);
    }

    @Test
    void lo_guardado_se_lee_completo_desde_la_base() {
        User creado = adapter.create(usuario("ana", "ana@correo.com"));
        em.clear();

        User leido = adapter.findByLogin("ana").orElseThrow();

        assertThat(leido).usingRecursiveComparison().isEqualTo(creado);
        assertThat(leido.getAuthorities()).containsExactly(User.ROLE_USER);
        assertThat(leido.getCreatedDate()).isEqualTo(FECHA);
    }

    @Test
    void findByLogin_de_un_login_inexistente_devuelve_vacio() {
        assertThat(adapter.findByLogin("nadie")).isEmpty();
    }

    @Test
    void existBy_distingue_los_que_existen_de_los_que_no() {
        adapter.create(usuario("ana", "ana@correo.com"));

        assertThat(adapter.existByLogin("ana")).isTrue();
        assertThat(adapter.existByLogin("otra")).isFalse();
        assertThat(adapter.existByEmail("ana@correo.com")).isTrue();
        assertThat(adapter.existByEmail("otra@correo.com")).isFalse();
    }

    @Test
    void create_con_un_login_repetido_lanza_LoginAlreadyUsedException() {
        adapter.create(usuario("ana", "ana@correo.com"));

        assertThatThrownBy(() -> adapter.create(usuario("ana", "otra@correo.com")))
                .isInstanceOf(LoginAlreadyUsedException.class);
    }

    @Test
    void create_con_un_email_repetido_lanza_EmailAlreadyUsedException() {
        adapter.create(usuario("ana", "ana@correo.com"));

        assertThatThrownBy(() -> adapter.create(usuario("otra", "ana@correo.com")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void la_base_rechaza_un_login_con_mayusculas() {
        assertThatThrownBy(() -> adapter.create(usuario("Ana", "ana@correo.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static User usuario(String login, String email) {
        return User.builder().login(login).passwordHash("$2a$10$" + "x".repeat(53)).firstName("Ana")
                .lastName("Paz").email(email).activated(true).langKey("es")
                .authorities(Set.of(User.ROLE_USER)).createdDate(FECHA).lastModifiedDate(FECHA).build();
    }
}
