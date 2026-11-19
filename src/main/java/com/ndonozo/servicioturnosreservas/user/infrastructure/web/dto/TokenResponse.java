package com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(@JsonProperty("id_token") String idToken) {
}