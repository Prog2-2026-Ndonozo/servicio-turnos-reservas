package com.ndonozo.servicioturnosreservas.User.domain.model;

import lombok.*;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    public static final String ROLE_USER = "ROLE_USER";

    private Long id;
    private String login;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String email;
    private String imageUrl;
    private boolean activated;
    private String langKey;
    private Set<String> authorities;
    private Instant createdDate;
    private Instant lastModifiedDate;
}
