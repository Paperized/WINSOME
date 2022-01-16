package it.winsome.common.network.enums;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface NetMessageHandlerInterface {
    SocketChannel getSocketChannel();
}
