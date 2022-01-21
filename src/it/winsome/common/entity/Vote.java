package it.winsome.common.entity;

import it.winsome.common.entity.enums.VoteType;

public class Vote {
    String from;
    VoteType type;
    boolean needIteration = true;

    public Vote() {}
    public Vote(String from, VoteType type) {
        this.from = from;
        this.type = type;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public VoteType getType() {
        return type;
    }

    public void setType(VoteType type) {
        this.type = type;
    }

    public boolean isNeedIteration() {
        return needIteration;
    }

    public void setNeedIteration(boolean needIteration) {
        this.needIteration = needIteration;
    }
}
