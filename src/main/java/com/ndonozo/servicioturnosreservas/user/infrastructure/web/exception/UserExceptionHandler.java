package com.ndonozo.servicioturnosreservas.user.infrastructure.web.exception;

import com.ndonozo.servicioturnosreservas.user.application.exception.EmailAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.application.exception.InvalidCredentialsException;
import com.ndonozo.servicioturnosreservas.user.application.exception.LoginAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto.FieldViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handle(MethodArgumentNotValidException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Hay campos inválidos");
        List<FieldViolation> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handle(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "El cuerpo de la petición no es un JSON válido");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}