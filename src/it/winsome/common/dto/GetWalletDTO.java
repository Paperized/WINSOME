package it.winsome.common.dto;

import it.winsome.common.entity.Transaction;
import it.winsome.common.entity.Wallet;
import it.winsome.common.network.NetMessage;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Wallet command data transfer
 */
public class GetWalletDTO {
    public Wallet wallet;

    public GetWalletDTO() {  }
    public GetWalletDTO(Wallet wallet) {
        this.wallet = wallet;
    }

    public static void netSerialize(NetMessage to, GetWalletDTO dto) {
        to.writeDouble(dto.wallet.getAmount());
        to.writeCollection(dto.wallet.getTransactions(), GetWalletDTO::netTransactionSerialize);
    }

    public static GetWalletDTO netDeserialize(NetMessage from) {
        GetWalletDTO dto = new GetWalletDTO();
        double amount = from.readDouble();
        Collection<Transaction> transactions = new ArrayList<>();
        from.readCollection(transactions, GetWalletDTO::netTransactionDeserialize);
        Wallet wallet = new Wallet(transactions);
        wallet.setAmount(amount);
        dto.wallet = wallet;
        return dto;
    }

    public static int netSize(GetWalletDTO list) {
        return 8 + NetMessage.getCollectionSize(list.wallet.getTransactions(), GetWalletDTO::netTransactionSize);
    }

    private static void netTransactionSerialize(NetMessage to, Transaction t) {
        to.writeLong(t.time.getTime());
        to.writeDouble(t.amount);
    }

    private static Transaction netTransactionDeserialize(NetMessage from) {
        return new Transaction(Timestamp.from(Instant.ofEpochSecond(from.readLong())),
                from.readDouble());
    }

    private static int netTransactionSize(Transaction transaction) {
        if(transaction == null) return 4;
        return 16; // 8 timestamp 8 double
    }
}
