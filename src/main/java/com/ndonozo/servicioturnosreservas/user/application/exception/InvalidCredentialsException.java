package com.ndonozo.servicioturnosreservas.user.application.exception;

public class InvalidCredentialException extends RuntimeException {
  public InvalidCredentialException(String message) {
    super(message);
  }
}
