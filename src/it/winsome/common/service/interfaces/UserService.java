package it.winsome.common.service.interfaces;

import it.winsome.common.entity.User;
import it.winsome.common.exception.NoTagsFoundException;
import it.winsome.common.exception.UserAlreadyExistsException;
import it.winsome.common.exception.UserNotExistsException;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface UserService extends Remote {
    User registerUser(String username, String password, String[] tags) throws RemoteException, UserAlreadyExistsException, NoTagsFoundException;
    void registerUserCallback(String username, UserCallback callbackObject) throws RemoteException, UserNotExistsException;
    void unregisterUserCallback(String username, UserCallback callbackObject) throws RemoteException, UserNotExistsException;
}
