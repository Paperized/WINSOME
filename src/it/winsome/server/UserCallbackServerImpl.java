package it.winsome.server;

import it.winsome.common.entity.*;
import it.winsome.common.exception.*;
import it.winsome.common.service.interfaces.UserCallbackServer;
import it.winsome.common.service.interfaces.UserCallbackClient;
import it.winsome.common.validation.Validator;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;

/**
 * Implementation of the RMI Server Interface
 */
public class UserCallbackServerImpl extends UnicastRemoteObject implements UserCallbackServer {
    private final ServerLogic logic;

    public UserCallbackServerImpl(ServerLogic logic) throws RemoteException {
        super();
        this.logic = logic;
    }

    @Override
    public User registerUser(String username, String password, String[] tags) throws RemoteException, InvalidParameterException {
        Validator.validateUsername(username);
        Validator.validatePassword(password);
        Validator.validateTags(Arrays.asList(tags));

        try {
            return logic.registerUser(username, password, tags);
        } catch (UserAlreadyExistsException e) {
            throw new InvalidParameterException("This username already exists!");
        } catch (NoTagsFoundException e) {
            throw new InvalidParameterException("No tags where found!");
        }
    }

    @Override
    public void registerUserCallback(String username, UserCallbackClient callbackObject) throws RemoteException, UserNotExistsException {
        Validator.validateUsername(username);

        logic.registerUserCallback(username, callbackObject);
    }

    @Override
    public void unregisterUserCallback(String username, UserCallbackClient callbackObject) throws RemoteException, UserNotExistsException {
        Validator.validateUsername(username);

        logic.unregisterUserCallback(username, callbackObject);
    }
}
