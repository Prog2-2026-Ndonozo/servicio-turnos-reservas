package com.ndonozo.servicioturnosreservas.user.domain.ports.out;

import com.ndonozo.servicioturnosreservas.user.domain.model.AuthToken;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;

public interface TokenIssuer {
    AuthToken issue(User user);
}


