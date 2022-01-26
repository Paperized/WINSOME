package it.winsome.server;

import it.winsome.common.entity.*;
import it.winsome.common.exception.*;
import it.winsome.common.service.interfaces.UserCallbackServer;
import it.winsome.common.service.interfaces.UserCallbackClient;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Implementation of the RMI Server Interface
 */
public class UserCallbackServerImpl extends UnicastRemoteObject implements UserCallbackServer {
    private ServerLogic logic;

    public UserCallbackServerImpl(ServerLogic logic) throws RemoteException {
        super();
        this.logic = logic;
    }

    @Override
    public User registerUser(String username, String password, String[] tags) throws RemoteException, UserAlreadyExistsException, NoTagsFoundException {
        return logic.registerUser(username, password, tags);
    }

    @Override
    public void registerUserCallback(String username, UserCallbackClient callbackObject) throws RemoteException, UserNotExistsException {
        logic.registerUserCallback(username, callbackObject);
    }

    @Override
    public void unregisterUserCallback(String username, UserCallbackClient callbackObject) throws RemoteException, UserNotExistsException {
        logic.unregisterUserCallback(username, callbackObject);
    }
}
