package it.winsome.common.entity;

import it.winsome.common.SynchronizedObject;
import it.winsome.common.entity.enums.VoteType;

import java.io.Serializable;

/**
 * This represents a vote in the social network, it can be assigned to anything that
 * inherits from BaseVotableEntity.
 * This entity can be synchronizable since it extends SynchronizedObject
 */
public class Vote extends SynchronizedObject implements Serializable {
    String from;
    VoteType type;
    boolean needIteration = true;

    public Vote() {}
    public Vote(String from, VoteType type) {
        this.from = from;
        this.type = type;
    }

    public String getFrom() {
        checkReadSynchronization();
        return from;
    }

    public VoteType getType() {
        checkReadSynchronization();
        return type;
    }

    public boolean isNeedIteration() {
        checkReadSynchronization();
        return needIteration;
    }

    public void setNeedIteration(boolean needIteration) {
        checkWriteSynchronization();
        this.needIteration = needIteration;
    }
}
