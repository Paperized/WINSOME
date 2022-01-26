package it.winsome.common.network;

import it.winsome.common.exception.InvalidParameterException;
import it.winsome.common.exception.SocketDisconnectedException;
import it.winsome.common.network.enums.NetMessageType;
import it.winsome.common.WinsomeHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Wrapper around a ByteBuffer, it has a fixed length and can be read or written but never both together
 * It has a way of reusing a previous net message by providing how much capacity is needed
 * It offers a way to read or write easily a message in a TCP connection or anything which work with NIO Channels
 * but not limited to those
 * It offers also partial read/write in case of Non blocking IO
 *
 * Provides validated input
 */
public class NetMessage {
    public static final int NULL_IDENTIFIER = Integer.MAX_VALUE;

    private boolean readOnly;
    private boolean hasChanged;
    private int messageLength;
    private NetMessageType type;
    private ByteBuffer data;
    private int nextByteWritable = 0;
    private int nextByteReadable = 0;
    private boolean isMessageTypeAvailable;
    private boolean isUnused;

    private NetMessage() { }

    /**
     * Create a new writable message or reuse the previous one if the capacity is met
     * @param message previous message
     * @param type header type
     * @param capacityNeeded capacity needed
     * @return A new message
     */
    public static NetMessage reuseWritableNetMessageOrCreate(NetMessage message, NetMessageType type, int capacityNeeded) {
        if(message == null) return writableNetMessage(type, capacityNeeded);
        if(message.getMaxCapacity() < capacityNeeded) {
            return writableNetMessage(type, capacityNeeded);
        }

        return reuseWritableNetMessage(message, type);
    }

    /**
     * Reuse an already used message by resetting it's state
     * @param message message
     * @param type header type
     * @return the same message resettled
     */
    public static NetMessage reuseWritableNetMessage(NetMessage message, NetMessageType type) {
        if(message == null) throw new NullPointerException("Message cannot be null!");
        message.type = type;
        message.messageLength = 8;
        message.hasChanged = true;
        message.nextByteWritable = 0;
        message.isMessageTypeAvailable = true;

        if(message.readOnly) {
            message.readOnly = false;
        } else {
            message.data.clear();
        }

        message.data.position(8);
        return message;
    }

    /**
     * Allocate a new writable message with a type and a capacity
     * @param type header type
     * @param maxCapacityMessage capacity needed
     * @return A new message
     */
    public static NetMessage writableNetMessage(NetMessageType type, int maxCapacityMessage) {
        NetMessage message = new NetMessage();
        maxCapacityMessage += 8;
        if(type == null) throw new NullPointerException("Type cannot be null");
        message.type = type;
        message.data = ByteBuffer.allocate(maxCapacityMessage);
        message.hasChanged = true;
        message.isMessageTypeAvailable = true;
        message.messageLength = 8;
        message.data.position(8);
        return message;
    }

    /**
     * Create a new readable message or reuse the previous one if the capacity is met
     * @param message previous message
     * @param capacityNeeded capacity needed
     * @return A new message
     */
    public static NetMessage reuseReadableNetMessageOrCreate(NetMessage message, int capacityNeeded) {
        capacityNeeded += 8;
        if(message == null) return readableNetMessage(ByteBuffer.allocate(capacityNeeded));
        if(message.getMaxCapacity() < capacityNeeded) {
            return readableNetMessage(ByteBuffer.allocate(capacityNeeded));
        }

        return reuseReadableNetMessage(message);
    }

    /**
     * Reuse an already used message by resetting it's state
     * @param message message
     * @return the same message resettled
     */
    public static NetMessage reuseReadableNetMessage(NetMessage message) {
        if(message == null) throw new NullPointerException("Message cannot be null!");
        message.hasChanged = false;
        message.data.clear();
        message.messageLength = message.getMaxCapacity();
        message.readOnly = true;
        message.nextByteReadable = message.nextByteWritable = 0;
        message.type = null;
        message.isMessageTypeAvailable = false;
        return message;
    }

    /**
     * Allocate a new readable message backed by a ByteBuffer data
     * @param data byte buffer
     * @return A new message
     */
    public static NetMessage readableNetMessage(ByteBuffer data) {
        NetMessage message = new NetMessage();
        if(data == null) throw new NullPointerException("Data cannot be null");
        data.clear();
        message.messageLength = data.getInt();
        if(message.messageLength > data.capacity()) throw new IllegalArgumentException("Message length cant be bigger then the ByteBuffer capacity");
        message.type = NetMessageType.fromId(data.getInt());
        message.data = data;
        message.isMessageTypeAvailable = true;
        message.readOnly = true;
        return message;
    }

    /**
     * Send a message to an udp address
     * @param udpSocket socket sender
     * @param address target address
     * @param port target port
     * @return true if the message was sent
     */
    public boolean sendMessage(DatagramSocket udpSocket, InetAddress address, int port) {
        try {
            ByteBuffer buf = getByteBuffer();
            DatagramPacket dp = new DatagramPacket(buf.array(), messageLength, address, port);
            udpSocket.send(dp);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Send a message to a writable channel
     * @param channel writable channel
     * @return true if the message was written fully
     */
    public boolean sendMessage(WritableByteChannel channel) throws SocketDisconnectedException {
        if(messageLength < 8) throw new IllegalArgumentException("NetMessage must be minimum 8 bytes");
        if(channel == null) throw new NullPointerException("SocketChannel cannot be null!");

        ByteBuffer messageBuffer = getByteBuffer();
        if(didStartWriting())
            messageBuffer.position(nextByteWritable);
        else
            messageBuffer.flip();

        int justSent;
        try {
            while((justSent = channel.write(messageBuffer)) > 0 && messageBuffer.hasRemaining())
                nextByteWritable += justSent;

            if(justSent == -1) {
                throw new SocketDisconnectedException();
            }
            nextByteWritable += justSent;

            return isWrittenFully();
        } catch(IOException ex) {
            throw new SocketDisconnectedException();
        }
    }

    /**
     * Read a message from a readable channel, an input message can be reused
     * This message CAN be partial in case of Non Blocking IO, always check isReadFully()
     * @param reuse reused message
     * @param channel readable channel
     * @return A new message
     * @throws SocketDisconnectedException in case of socket disconnection
     */
    public static NetMessage fromChannel(NetMessage reuse, ReadableByteChannel channel) throws SocketDisconnectedException {
        try {
            NetMessage newMessage = readHeaderMessage(reuse, channel);
            int lastRead;
            while((lastRead = channel.read(newMessage.data)) > 0 && newMessage.data.hasRemaining())
                newMessage.nextByteReadable += lastRead;

            if(lastRead == -1) {
                throw new SocketDisconnectedException();
            }

            newMessage.nextByteReadable += lastRead;
            if(newMessage.nextByteReadable >= 8) {
                newMessage.isMessageTypeAvailable = true;
            }

            return newMessage;
        } catch (Exception ex) {
            throw new SocketDisconnectedException();
        }
    }

    /**
     * Keep reading from a channel, used in case of the previous operation did not finish reading all the message
     * Often used in Non Blocking IO
     * @param netMessage partially read message
     * @param channel readable channel
     * @return true if the message is read fully
     * @throws SocketDisconnectedException in case of socket disconnection
     */
    public static boolean keepReadingFromChannel(NetMessage netMessage, ReadableByteChannel channel) throws SocketDisconnectedException {
        if(netMessage == null || channel == null) throw new NullPointerException("Parameters cannot be null!");
        if(!netMessage.didStartReading()) {
            throw new IllegalArgumentException("Use this method after calling NetMessage.fromHandler(...)");
        }

        if(netMessage.isReadFully()) return true;
        try {
            int startingIndex = netMessage.nextByteReadable;
            int lastRead;
            while((lastRead = channel.read(netMessage.data)) > 0 && netMessage.data.hasRemaining())
                netMessage.nextByteReadable += lastRead;

            if(lastRead == -1) {
                throw new SocketDisconnectedException();
            }
            netMessage.nextByteReadable += lastRead;

            if(startingIndex < 7 && netMessage.nextByteReadable >= 8) {
                netMessage.isMessageTypeAvailable = true;
            }

            return netMessage.isReadFully();
        } catch (IOException e) {
            throw new SocketDisconnectedException();
        }
    }

    /**
     * Read the first 4 bytes (obligatory) of the next message, it blocks (even in Non Blocking IO) until it's done
     * @param reuse a reusable message
     * @param channel readable channel
     * @return A new message
     * @throws SocketDisconnectedException in case of disconnection
     */
    private static NetMessage readHeaderMessage(NetMessage reuse, ReadableByteChannel channel) throws SocketDisconnectedException {
        if(channel == null) throw new NullPointerException("Channel cannot be null!");
        ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        int lastRead;
        try {
            while ((lastRead = channel.read(lengthBuffer)) >= 0 && lengthBuffer.hasRemaining());
            if (lastRead == -1) {
                throw new SocketDisconnectedException();
            }

            int messageLength = lengthBuffer.getInt(0);
            NetMessage newMessage = NetMessage.reuseReadableNetMessageOrCreate(reuse, messageLength - 8);
            newMessage.messageLength = messageLength;
            newMessage.data.limit(newMessage.messageLength);
            newMessage.data.position(0);
            newMessage.data.putInt(messageLength);
            newMessage.isMessageTypeAvailable = false;
            newMessage.type = null;
            newMessage.nextByteReadable = 4;
            return newMessage;
        } catch (IOException ex) {
            throw new SocketDisconnectedException();
        }
    }

    public boolean isPeekingNull() {
        return this.data.getInt(this.data.position()) == NULL_IDENTIFIER;
    }

    /**
     * Read an integer from the message and throw an exception if the validation fails
     * @param validation validation function
     * @return the validated integer
     * @throws InvalidParameterException if invalid
     */
    public int readInt(Consumer<Integer> validation) throws InvalidParameterException {
        int value = readInt();
        validation.accept(value);
        return value;
    }

    /**
     * Read an double from the message and throw an exception if the validation fails
     * @param validation validation function
     * @return the validated double
     * @throws InvalidParameterException if invalid
     */
    public double readDouble(Consumer<Double> validation) throws InvalidParameterException {
        double value = readDouble();
        validation.accept(value);
        return value;
    }

    /**
     * Read an long from the message and throw an exception if the validation fails
     * @param validation validation function
     * @return the validated long
     * @throws InvalidParameterException if invalid
     */
    public long readLong(Consumer<Long> validation) throws InvalidParameterException {
        long value = readLong();
        validation.accept(value);
        return value;
    }

    /**
     * Read an string from the message and throw an exception if the validation fails
     * @param validation validation function
     * @return the validated string
     * @throws InvalidParameterException if invalid
     */
    public String readString(Consumer<String> validation) throws InvalidParameterException {
        String value = readString();
        validation.accept(value);
        return value;
    }

    /**
     * Read an object from the message and throw an exception if the validation fails
     * @param fn the function used to create the object
     * @param validation validation function
     * @return the validated object
     * @throws InvalidParameterException if invalid
     */
    public <T> T readObject(Function<NetMessage, T> fn, Consumer<T> validation)
            throws InvalidParameterException {
        T obj = readObject(fn);
        validation.accept(obj);
        return obj;
    }

    public void readCollectionStringValidation(Collection<String> col, Consumer<Collection<String>> validation)
            throws InvalidParameterException {
        readCollection(col);
        validation.accept(col);
    }

    public <T> void readCollection(Collection<T> col, Function<NetMessage, T> deserialization,
                                   Consumer<Collection<T>> validation)
            throws InvalidParameterException {
        readCollection(col, deserialization);
        validation.accept(col);
    }

    public int readInt() {
        if(this.data.position() + 4 > messageLength) throw new IndexOutOfBoundsException();
        return this.data.getInt();
    }

    public double readDouble() {
        if(this.data.position() + 8 > messageLength) throw new IndexOutOfBoundsException();
        return this.data.getDouble();
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

    public NetMessage writeDouble(double value) {
        if(readOnly) throw new ReadOnlyBufferException();

        this.data.putDouble(value);
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
        if(readOnly && isMessageTypeAvailable) {
            if(type == null) {
                type = NetMessageType.fromId(data.getInt(4));
            }

            return type;
        }
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
            // update type
            getType();
            return data.asReadOnlyBuffer();
        }

        return data.duplicate();
    }

    /**
     * Check if the message started a write operation to a channel
     * @return true if it has
     */
    public boolean didStartWriting() {
        return nextByteWritable > 0;
    }

    /**
     * Check if the message started a read operation to a channel
     * @return true if it has
     */
    public boolean didStartReading() {
        return nextByteReadable > 0;
    }

    /**
     * Check if the message was written fully to a channel
     * @return true if it has
     */
    public boolean isWrittenFully() {
        return nextByteWritable == data.position();
    }

    /**
     * Check if the message was read fully from a channel
     * @return true if it has
     */
    public boolean isReadFully() {
        return nextByteReadable == data.position();
    }

    public int getMaxCapacity() {
        return data.capacity();
    }

    public static int getStringSize(String str) {
        if(str == null) return 4;
        return 4 + str.getBytes(StandardCharsets.UTF_8).length;
    }

    public static int getStringSize(String... strings) {
        int count = 4 * strings.length;
        for(String str : strings) {
            if(str != null) {
                count += str.getBytes(StandardCharsets.UTF_8).length;
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

    public boolean isMessageTypeAvailable() {
        return isMessageTypeAvailable;
    }

    /**
     * Must be used before reading from a channel to fix the pointers of the ByteBuffer
     */
    public void prepareRead() {
        data.flip();
        data.position(8);
    }

    public boolean isUnused() {
        return isUnused;
    }

    public void setUnused(boolean unused) {
        isUnused = unused;
    }
}
