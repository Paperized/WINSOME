package it.winsome.server.session;

import it.winsome.common.entity.User;
import it.winsome.common.network.NetMessage;

/**
 * It represents a session of a generic SelectionKey (NIO) or more practically, a client socket connection
 */
public class ConnectionSession {
    private NetMessage writableMessage;
    private NetMessage readableMessage;
    private User userLogged;

    public void setWritableMessage(NetMessage writableMessage) {
        this.writableMessage = writableMessage;
    }

    public void setReadableMessage(NetMessage readableMessage) {
        this.readableMessage = readableMessage;
    }

    public NetMessage getWritableMessage() {
        return writableMessage;
    }

    public NetMessage getReadableMessage() {
        return readableMessage;
    }

    public User getUserLogged() {
        return userLogged;
    }

    public void setUserLogged(User userLogged) {
        this.userLogged = userLogged;
    }
}
