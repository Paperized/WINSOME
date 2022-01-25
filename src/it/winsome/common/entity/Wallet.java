package it.winsome.common.entity;

import it.winsome.common.SynchronizedObject;
import it.winsome.common.entity.enums.CurrencyType;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This represents a wallet in the social network, it includes a total amount and a transaction list
 * This entity can be synchronizable since it extends SynchronizedObject
 */
public class Wallet extends SynchronizedObject implements Serializable {
    private double amount;
    private transient double rateBtc;
    private Collection<Transaction> transactions;

    public Wallet() {
        transactions = new ArrayList<>();
    }

    public Wallet(Collection<Transaction> transactions) {
        this.transactions = transactions;
    }

    public double getAmount() {
        checkReadSynchronization();
        return amount;
    }

    public double getAmount(CurrencyType currencyType) {
        checkReadSynchronization();
        if(currencyType == CurrencyType.Winsome) {
            return amount;
        } else if(currencyType == CurrencyType.Bitcoin) {
            return amount * rateBtc;
        }

        return amount;
    }

    public void setAmount(double amount) {
        checkWriteSynchronization();
        this.amount = amount;
    }

    public void setRateInBTC(double rateBTC) {
        checkWriteSynchronization();
        this.rateBtc = rateBTC;
    }

    public void addTransaction(double amount) {
        addTransaction(Timestamp.from(Instant.now()), amount);
    }

    public void addTransaction(Timestamp time, double amount) {
        checkWriteSynchronization();
        if(amount == 0) return;
        this.amount += amount;
        transactions.add(new Transaction(time == null ?
                Timestamp.from(Instant.now()) : time, amount));
    }

    public Collection<Transaction> getTransactions() {
        checkReadSynchronization();
        return Collections.unmodifiableCollection(transactions);
    }

    public Collection<Transaction> getTransactionsInCurrency(CurrencyType type) {
        checkReadSynchronization();
        if(type == CurrencyType.Winsome) {
            return new ArrayList<>(transactions);
        } else if(type == CurrencyType.Bitcoin) {
            return transactions.stream().map(x -> new Transaction(x.time, x.amount * this.rateBtc))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>(transactions);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        checkReadSynchronization();
        return super.clone();
    }

    public Wallet deepCopyByCurrency(CurrencyType currencyType) {
        checkReadSynchronization();
        Wallet wallet = new Wallet();
        if(currencyType == CurrencyType.Winsome) {
            wallet.amount = amount;
        } else if(currencyType == CurrencyType.Bitcoin) {
            wallet.amount = amount * rateBtc;
        } else {
            wallet.amount = amount;
        }

        wallet.transactions = getTransactionsInCurrency(currencyType);
        return wallet;
    }

    @Override
    public String toString() {
        checkReadSynchronization();
        return "Wallet{" +
                "amount=" + amount +
                ", transactions=" + transactions +
                '}';
    }
}
