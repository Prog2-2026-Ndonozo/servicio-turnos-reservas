package com.ndonozo.servicioturnosreservas.user.application.exception;

public class LoginAlreadyUseException extends RuntimeException {
  public LoginAlreadyUseException(String message) {
    super(message);
  }
}
