package it.winsome.client;

import it.winsome.client.interfaces.WalletNotification;
import it.winsome.common.network.NetMessage;
import it.winsome.common.network.enums.NetMessageType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;

/**
 * Thread that check any incoming message from a multicast address
 */
public class MulticastChecker extends Thread {
    WalletNotification walletNotification;
    MulticastSocket multicastSocket;

    public MulticastChecker(WalletNotification walletNotification, InetAddress multicastAddress, int multicastPort) throws IOException {
        multicastSocket = new MulticastSocket(null);
        multicastSocket.setReuseAddress(true);
        multicastSocket.bind(new InetSocketAddress(multicastPort));
        multicastSocket.setTimeToLive(1);
        multicastSocket.joinGroup(multicastAddress);

        this.walletNotification = walletNotification;
    }

    @Override
    public void run() {
        // I cant read the header partially in udp so this should be enough
        byte[] receiveBuffer = new byte[8192];
        int retry = 2;
        while(retry > 0) {
            if(isInterrupted())
                break;

            DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                multicastSocket.receive(packet);
                NetMessage message = NetMessage.readableNetMessage(
                        ByteBuffer.wrap(packet.getData(), 0, packet.getLength()));
                if(message.getType() == NetMessageType.NotifyWallet) {
                    walletNotification.onWalletUpdated();
                }
                retry = 2;
            } catch (IOException e) {
                if(isInterrupted()) {
                    break;
                } else {
                    e.printStackTrace();
                    retry--;
                }
            }
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        multicastSocket.close();
    }
}
