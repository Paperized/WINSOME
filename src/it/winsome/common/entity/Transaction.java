package it.winsome.common.entity;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * This represents a transaction in the social network, every time the server iterates through the posts
 * it starts calculating the rewards of each user and add them in the transaction list
 */
public class Transaction implements Serializable {
    public final Timestamp time;
    public final double  amount;

    public Transaction(Timestamp time, double amount) {
        if(time == null) throw new NullPointerException("Time cannot be null!");
        this.time = time;
        this.amount = amount;
    }
}
