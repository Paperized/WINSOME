package it.winsome.common.exception;

/**
 * Exception used to avoid deadlock inside synhcronized entities
 * e.g when trying to obtain a writelock and a readlock is owned
 */
public class DeadlockPreventionException extends Exception{
}
