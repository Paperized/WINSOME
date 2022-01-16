package it.winsome.client;

import it.winsome.common.network.enums.NetMessageHandlerInterface;
import it.winsome.server.ServerMain;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class TcpClient implements NetMessageHandlerInterface {
    SocketChannel socketChannel;
    InetSocketAddress serverAddress;

    public TcpClient() { }

    public TcpClient(String ip, int port) {
        serverAddress = new InetSocketAddress(ip, port);
    }

    public boolean connect() {
        if(socketChannel != null && socketChannel.isConnected()) {
            return true;
        }

        try {
            socketChannel = SocketChannel.open();
            return socketChannel.connect(serverAddress);
        } catch(IOException ex) {
            return false;
        }
    }

    public boolean connect(String ip, int port) {
        if(socketChannel != null && socketChannel.isConnected()) {
            return true;
        }

        try {
            socketChannel = SocketChannel.open();
            serverAddress = new InetSocketAddress(ip, port);
            return socketChannel.connect(serverAddress);
        } catch(IOException ex) {
            return false;
        }
    }

    public boolean isConnected() {
        return socketChannel != null && socketChannel.isConnected();
    }

    public void disconnect() throws IOException {
        if(socketChannel != null) {
            socketChannel.close();
        }
    }

    @Override
    public SocketChannel getSocketChannel() {
        return socketChannel;
    }
}
