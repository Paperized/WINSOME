package it.winsome.client;

import it.winsome.client.interfaces.WalletNotification;
import it.winsome.common.exception.SocketDisconnectedException;
import it.winsome.common.network.NetMessage;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;

/**
 * Manages all kinds of connection like TCP and Multicast, runs in the same thread and provide asynchronous wallet
 * notifications
 */
public class ClientConnector {
    MulticastChecker multicastChecker;
    SocketChannel socketChannel;
    InetSocketAddress serverAddress;
    InetAddress multicastAddress;
    int multicastPort;

    WalletNotification walletNotifier;

    public ClientConnector(String tcpIp, int port, String multicastIp, int multicastPort) throws UnknownHostException {
        serverAddress = new InetSocketAddress(tcpIp, port);
        this.multicastAddress = InetAddress.getByName(multicastIp);
        this.multicastPort = multicastPort;
    }

    /**
     * Start the connection TCP and Multicast to the server
     * @return true if connected
     */
    public boolean startConnector() {
        try {
            multicastChecker = new MulticastChecker(walletNotifier, multicastAddress, multicastPort);
        } catch (IOException e) {
            return false;
        }

        if(socketChannel == null || !socketChannel.isConnected()) {
            try {
                socketChannel = SocketChannel.open();
                if(!socketChannel.connect(serverAddress))
                    return false;
            } catch(IOException ex) {
                return false;
            }
        }

        multicastChecker.start();
        return true;
    }

    /**
     * Check if the client is connected
     * @return true if connected
     */
    public boolean isConnected() {
        return socketChannel != null && socketChannel.isConnected();
    }

    /**
     * Disconnect from the server
     * @throws IOException errors during the socket disconnection
     */
    public void disconnect() throws IOException {
        if(socketChannel != null) {
            socketChannel.close();
        }
        if(multicastChecker != null) {
            multicastChecker.interrupt();
        }
    }

    /**
     * Provide a callback to be updated
     * @param walletNotifier interface callback
     */
    public void setWalletNotifier(WalletNotification walletNotifier) {
        this.walletNotifier = walletNotifier;
    }

    /**
     * Send a TCP message to the server
     * @param message message to be sent
     * @return always return true
     * @throws SocketDisconnectedException if the server disconnected
     */
    public boolean sendTcpMessage(NetMessage message) throws SocketDisconnectedException {
        return message.sendMessage(socketChannel);
    }

    /**
     * Wait until the client assemble a message
     * @param reuse message to be reused
     * @return the received message, might return the same message if the capacity was enough
     * @throws SocketDisconnectedException if the server disconnected
     */
    public NetMessage receiveTcpMessage(NetMessage reuse) throws SocketDisconnectedException {
        return NetMessage.fromChannel(reuse, socketChannel);
    }
}
