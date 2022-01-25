package it.winsome.common.service.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface UserCallbackClient extends Remote {
    void onFollowReceived(String follower) throws RemoteException;
    void onFollowRemoved(String follower) throws RemoteException;
}
