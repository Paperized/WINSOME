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
    private MulticastChecker multicastChecker;
    private SocketChannel socketChannel;
    private final InetSocketAddress serverAddress;
    private InetAddress multicastAddress;
    private int multicastPort;

    WalletNotification walletNotifier;

    public ClientConnector(String tcpIp, int port) {
        serverAddress = new InetSocketAddress(tcpIp, port);
    }

    /**
     * Start the connection TCP
     * @return true if connected
     */
    public boolean startTCP() {
        if(socketChannel == null || !socketChannel.isConnected()) {
            try {
                socketChannel = SocketChannel.open();
                if(!socketChannel.connect(serverAddress))
                    return false;
            } catch(IOException ex) {
                return false;
            }
        }

        return true;
    }

    /**
     * start the multicast channel
     * @param multicastIp multicast ip
     * @param multicastPort local port
     * @return true if started
     */
    public boolean startMulticast(String multicastIp, int multicastPort, WalletNotification notification) {
        try {
            multicastAddress = InetAddress.getByName(multicastIp);
            this.multicastPort = multicastPort;
            walletNotifier = notification;
            multicastChecker = new MulticastChecker(walletNotifier, multicastAddress, multicastPort);
            multicastChecker.start();
            return true;
        } catch (IOException e) {
            return false;
        }
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
