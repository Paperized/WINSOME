package it.winsome.common.service.interfaces;

import it.winsome.common.entity.User;
import it.winsome.common.exception.InvalidParameterException;
import it.winsome.common.exception.NoTagsFoundException;
import it.winsome.common.exception.UserAlreadyExistsException;
import it.winsome.common.exception.UserNotExistsException;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI Server object used for providing services
 */
public interface UserCallbackServer extends Remote {
    /**
     * Creates a new user
     * @param username username
     * @param password password hashed
     * @param tags tags, min 0 max 5
     * @return a new user with those characteristics
     * @throws RemoteException remote exception
     */
    User registerUser(String username, String password, String[] tags) throws RemoteException, InvalidParameterException;
    void registerUserCallback(String username, UserCallbackClient callbackObject) throws RemoteException, UserNotExistsException;
    void unregisterUserCallback(String username, UserCallbackClient callbackObject) throws RemoteException, UserNotExistsException;
}
