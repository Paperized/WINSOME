package it.winsome.common.exception;

public class InvalidParameterException extends RuntimeException {
    public InvalidParameterException() { }
    public InvalidParameterException(String msg) { super(msg); }
}
