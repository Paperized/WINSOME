package it.winsome.common.network;

import it.winsome.common.exception.SocketDisconnectedException;
import it.winsome.common.network.enums.NetMessageHandlerInterface;
import it.winsome.common.network.enums.NetMessageType;
import it.winsome.common.WinsomeHelper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class NetMessage {
    public static final int NULL_IDENTIFIER = Integer.MAX_VALUE;

    private boolean readOnly;
    private boolean hasChanged;
    private int messageLength;
    private NetMessageType type;
    private ByteBuffer data;

    private NetMessage() { }

    public static NetMessage reuseNetMessageOrCreate(NetMessage message, NetMessageType type, int capacityNeeded) {
        capacityNeeded += 8;
        if(message == null) return writableNetMessage(type, capacityNeeded);
        if(message.getMaxCapacity() < capacityNeeded) {
            return writableNetMessage(type, capacityNeeded);
        }

        return reuseNetMessage(message, type);
    }

    public static NetMessage reuseNetMessage(NetMessage message, NetMessageType type) {
        if(message == null) throw new NullPointerException("Message cannot be null!");
        message.type = type;
        message.messageLength = 8;
        message.hasChanged = true;

        if(message.readOnly) {
            message.readOnly = false;
        } else {
            message.data.clear();
        }

        message.data.position(8);
        return message;
    }

    public static NetMessage writableNetMessage(NetMessageType type, int maxCapacityMessage) {
        NetMessage message = new NetMessage();
        maxCapacityMessage += 8;
        if(type == null) throw new NullPointerException("Type cannot be null");
        message.type = type;
        message.data = ByteBuffer.allocate(maxCapacityMessage);
        message.hasChanged = true;
        message.messageLength = 8;
        message.data.position(8);
        return message;
    }

    public static NetMessage readableNetMessage(ByteBuffer data) {
        NetMessage message = new NetMessage();
        if(data == null) throw new NullPointerException("Data cannot be null");
        data.flip();
        message.messageLength = data.getInt();
        if(message.messageLength > data.capacity()) throw new IllegalArgumentException("Message length cant be bigger then the ByteBuffer capacity");
        message.type = NetMessageType.fromId(data.getInt());
        message.data = data;
        message.readOnly = true;
        return message;
    }

    public void sendMessage(NetMessageHandlerInterface handler) throws SocketDisconnectedException {
        if(messageLength < 8) throw new IllegalArgumentException("NetMessage must be minimum 8 bytes");
        SocketChannel channel = handler.getSocketChannel();
        if(channel == null) throw new NullPointerException("SocketChannel cannot be null!");

        ByteBuffer messageBuffer = getByteBuffer();
        messageBuffer.flip();
        int justSent;
        try {
            while((justSent = channel.write(messageBuffer)) >= 0 && messageBuffer.hasRemaining());

            WinsomeHelper.printfDebug("Sent message size %d", messageBuffer.position());
        } catch(IOException ex) {
            throw new SocketDisconnectedException();
        }
    }

    public static NetMessage fromHandler(NetMessageHandlerInterface handler) throws SocketDisconnectedException {
        SocketChannel socketChannel = handler.getSocketChannel();
        ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        int lastRead;
        try {
            while((lastRead = socketChannel.read(lengthBuffer)) >= 0 && lengthBuffer.hasRemaining());
            if(lastRead == -1) {
                throw new SocketDisconnectedException();
            }

            int messageLength = lengthBuffer.getInt(0);
            ByteBuffer fullBuffer = ByteBuffer.allocate(messageLength);
            fullBuffer.putInt(messageLength);
            while((lastRead = socketChannel.read(fullBuffer)) >= 0 && fullBuffer.hasRemaining());
            if(lastRead == -1) {
                throw new SocketDisconnectedException();
            }

            WinsomeHelper.printfDebug("Received message size %d", messageLength);
            return NetMessage.readableNetMessage(fullBuffer);
        } catch (IOException ex) {
            throw new SocketDisconnectedException();
        }
    }

    public boolean isPeekingNull() {
        return this.data.getInt(this.data.position()) == NULL_IDENTIFIER;
    }

    public int readInt() {
        if(this.data.position() + 4 > messageLength) throw new IndexOutOfBoundsException();
        return this.data.getInt();
    }

    public long readLong() {
        if(this.data.position() + 8 > messageLength) throw new IndexOutOfBoundsException();
        return this.data.getLong();
    }

    public String readString() {
        if(this.data.position() + 4 > messageLength) throw new IndexOutOfBoundsException();
        int len = this.data.getInt();
        if(len == 0) {
            return "";
        }
        if(this.data.position() + len > messageLength) throw new IndexOutOfBoundsException("Current position " + this.data.position() + " needed to read to " + this.data.position() + len);

        byte[] strBytes = new byte[len];
        this.data.get(strBytes);
        return new String(strBytes, StandardCharsets.UTF_8);
    }

    public NetMessage writeNull() {
        return writeInt(NULL_IDENTIFIER);
    }

    public NetMessage writeInt(int value) {
        if(readOnly) throw new ReadOnlyBufferException();

        this.data.putInt(value);
        this.messageLength += 4;
        if(!hasChanged) hasChanged = true;
        return this;
    }

    public NetMessage writeLong(long value) {
        if(readOnly) throw new ReadOnlyBufferException();

        this.data.putLong(value);
        this.messageLength += 8;
        if(!hasChanged) hasChanged = true;
        return this;
    }

    public NetMessage writeString(String str) {
        if(readOnly) throw new ReadOnlyBufferException();
        if(str == null || str.equals("")) {
            this.data.putInt(0);
            messageLength += 4;
            if(!hasChanged) hasChanged = true;
            return this;
        }

        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        this.data.putInt(bytes.length);
        messageLength += 4;
        if(!hasChanged) hasChanged = true;

        this.data.put(bytes);
        messageLength += bytes.length;
        return this;
    }

    public <T> boolean writeNullIfInvalid(T obj) {
        if(obj == null) {
            writeNull();
            return true;
        }

        return false;
    }

    public NetMessage writeCollection(Collection<String> col) {
        if(col == null) {
            writeInt(0);
            return this;
        }

        writeInt(col.size());
        for(String el : col) {
            writeString(el);
        }

        return this;
    }

    public void readCollection(Collection<String> container) {
        if(container == null) throw new NullPointerException("Container cannot be null!");
        for(int i = readInt(); i > 0; i--) {
            container.add(readString());
        }
    }

    public <R> NetMessage writeCollection(Collection<R> col, BiConsumer<NetMessage, R> bc) {
        if(bc == null) throw new NullPointerException("Consumer cant be null!");
        if(col == null) {
            writeInt(0);
            return this;
        }

        writeInt(col.size());
        for(R el : col) {
            bc.accept(this, el);
        }

        return this;
    }

    public <R> void readCollection(Collection<R> container, Function<NetMessage, R> fn) {
        if(fn == null) throw new NullPointerException("Function cant be null!");
        if(container == null) throw new NullPointerException("Container cannot be null!");
        for(int i = readInt(); i > 0; i--) {
            container.add(fn.apply(this));
        }
    }

    public <R> R readObject(Function<NetMessage, R> fn) {
        if(fn == null) throw new NullPointerException("Function cant be null!");
        return fn.apply(this);
    }

    public <R> NetMessage writeObject(R obj, BiConsumer<NetMessage, R> bc) {
        if(bc == null) throw new NullPointerException("Consumer cant be null!");
        bc.accept(this, obj);
        return this;
    }

    public NetMessageType getType() {
        return type;
    }

    public int getMessageLength() {
        return messageLength;
    }

    public void setType(NetMessageType type) {
        this.type = type;
        if(!hasChanged) hasChanged = true;
    }

    public ByteBuffer getByteBuffer() {
        if(hasChanged) {
            this.data.putInt(0, messageLength);
            this.data.putInt(4, type.getId());
            hasChanged = false;
        }

        if(readOnly) {
            return data.asReadOnlyBuffer();
        }

        return data.duplicate();
    }

    public ByteBuffer getTest() {
        return data;
    }

    public int getMaxCapacity() {
        return data.capacity();
    }

    public static int getStringSize(String str) {
        if(str == null) return 4;
        return 4 + str.length();
    }

    public static int getStringSize(String... strings) {
        int count = 4 * strings.length;
        for(String str : strings) {
            if(str != null) {
                count += str.length();
            }
        }
        return count;
    }

    public static int getCollectionSize(Collection<String> col) {
        if(col == null) return 4;
        int size = 4;
        for(String str : col) {
            size += getStringSize(str);
        }
        return size;
    }

    public static <T> int getCollectionSize(Collection<T> col, Function<T, Integer> fnSize) {
        if(col == null) return 4;
        int size = 4;
        for(T el : col) {
            size += fnSize.apply(el);
        }
        return size;
    }
}
