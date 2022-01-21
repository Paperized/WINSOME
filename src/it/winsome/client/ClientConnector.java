package it.winsome.client;

import it.winsome.common.network.enums.NetConnectionType;
import it.winsome.common.network.enums.NetClientConnector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class ClientConnector implements NetClientConnector {
    SocketChannel socketChannel;
    InetSocketAddress serverAddress;

    public ClientConnector() { }

    public ClientConnector(String ip, int port) {
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
    public WritableByteChannel getWritableChannel(NetConnectionType type) {
        if(type == null) throw new NullPointerException("Connection type cannot be null");
        if(type == NetConnectionType.TCP || type == NetConnectionType.Default) {
            return socketChannel;
        } else if(type == NetConnectionType.Multicast) {
            return null;
        } else {
            throw new UnsupportedOperationException("Connection type " + type + " is not supported in this handler!");
        }
    }

    @Override
    public ReadableByteChannel getReadableChannel(NetConnectionType type) {
        if(type == null) throw new NullPointerException("Connection type cannot be null");
        if(type == NetConnectionType.TCP || type == NetConnectionType.Default) {
            return socketChannel;
        } else if(type == NetConnectionType.Multicast) {
            return null;
        } else {
            throw new UnsupportedOperationException("Connection type " + type + " is not supported in this handler!");
        }
    }
}
