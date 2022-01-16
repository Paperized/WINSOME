package it.winsome.common.exception;

public class NoAuthorizationException extends Exception {
    public NoAuthorizationException() { super(); }
    public NoAuthorizationException(String message) { super(message); }
}
