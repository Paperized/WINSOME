package it.winsome.client;

import it.winsome.common.entity.User;
import it.winsome.common.service.interfaces.UserCallback;
import it.winsome.common.WinsomeHelper;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

public class ClientSocialState extends RemoteObject implements UserCallback {
    private User currentUser;
    private boolean loggedIn;

    public ClientSocialState() {
        super();
    }

    public void setLogin(User currentUser) {
        this.currentUser = currentUser;
        loggedIn = true;
    }

    public void logout() {
        loggedIn = false;
    }

    @Override
    public void onFollowReceived(String follower) throws RemoteException {
        if(!loggedIn) return;
        currentUser.addUserFollowing(follower);
        WinsomeHelper.printfDebug("> %s now follows you!", follower);
    }

    @Override
    public void onFollowRemoved(String follower) throws RemoteException {
        if(!loggedIn) return;
        currentUser.removeUserFollowing(follower);
        WinsomeHelper.printfDebug("> %s unfollowed you!", follower);
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }
}
