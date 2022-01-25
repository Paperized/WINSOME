package it.winsome.common.exception;

/**
 * Exception used everything an action does require a valid authorization
 */
public class NoAuthorizationException extends Exception {
    public NoAuthorizationException() { super(); }
    public NoAuthorizationException(String message) { super(message); }
}
