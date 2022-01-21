package it.winsome.common.network.enums;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public interface NetClientConnector {
    WritableByteChannel getWritableChannel(NetConnectionType type);
    ReadableByteChannel getReadableChannel(NetConnectionType type);
}
