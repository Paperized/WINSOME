package it.winsome.server.workers;

import it.winsome.common.WinsomeHelper;
import it.winsome.common.exception.SocketDisconnectedException;
import it.winsome.server.ServerLogic;
import it.winsome.server.session.ConnectionSession;
import it.winsome.server.ServerConnector;
import it.winsome.server.ServerMain;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

public class WriterRequestHandler implements Runnable {
    private static ServerLogic serverLogic;
    private final ServerConnector server;
    private final ConnectionSession session;
    private final WritableByteChannel writableByteChannel;
    private final SelectionKey key;

    public WriterRequestHandler(ServerConnector server, SelectionKey key) {
        if(serverLogic == null)
            serverLogic = ServerMain.getServerLogic();

        this.server = server;
        this.key = key;
        writableByteChannel = (WritableByteChannel) key.channel();
        session = (ConnectionSession) key.attachment();
        this.key.interestOps(0);
    }

    @Override
    public void run() {
        if(!writableByteChannel.isOpen()) {
            onClientDisconnected();
            WinsomeHelper.printlnDebug("Cannot write back to client, disconnecting it from the server!");
            return;
        }

        boolean didFinishWrite;
        try {
            didFinishWrite = session.getWritableMessage().sendMessage(writableByteChannel);
        } catch (SocketDisconnectedException e) {
            onClientDisconnected();
            WinsomeHelper.printlnDebug("Cannot write back to client, disconnecting it from the server!");
            return;
        }

        if(didFinishWrite) {
            key.interestOps(SelectionKey.OP_READ);
        } else {
            key.interestOps(SelectionKey.OP_WRITE);
        }
        server.onHandlerFinish();
    }

    private void onClientDisconnected() {
        serverLogic.removeSession(key);
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
