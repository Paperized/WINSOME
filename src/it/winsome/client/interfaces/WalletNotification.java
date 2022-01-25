package it.winsome.client.interfaces;

/**
 * Interfaces used to receive updates for wallet
 */
public interface WalletNotification {
    /**
     * Callback when an update is received
     */
    void onWalletUpdated();
}
