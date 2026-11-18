package com.ndonozo.servicioturnosreservas.user.application.exception;

public class LoginAlreadyUsedException extends RuntimeException {
  public LoginAlreadyUsedException(String message) {
    super(message);
  }
}
