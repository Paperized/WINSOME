package it.winsome.common.service.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI Client object used for callbacks
 */
public interface UserCallbackClient extends Remote {
    /**
     * Called everytime a follow is received
     * @param follower follow received
     * @throws RemoteException
     */
    void onFollowReceived(String follower) throws RemoteException;
    /**
     * Called everytime a follow is removed
     * @param follower follow removed
     * @throws RemoteException
     */
    void onFollowRemoved(String follower) throws RemoteException;
}
