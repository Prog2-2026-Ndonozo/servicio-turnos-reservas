package com.ndonozo.servicioturnosreservas.user.infrastructure.web.exception;

import com.ndonozo.servicioturnosreservas.user.application.exception.EmailAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.application.exception.InvalidCredentialsException;
import com.ndonozo.servicioturnosreservas.user.application.exception.LoginAlreadyUsedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(LoginAlreadyUsedException.class)
    public ProblemDetail handle(LoginAlreadyUsedException e) {
        return problem(HttpStatus.CONFLICT, "LOGIN_ALREADY_USED", e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handle(EmailAlreadyUsedException e) {
        return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handle(InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}