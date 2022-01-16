package it.winsome.common.entity;

import it.winsome.common.entity.abstracts.BaseVotableEntity;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.network.NetMessage;

import java.util.Objects;

public class Vote {
    private String from;
    private VoteType type;

    public Vote() { }

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

    public VoteType toggleVoteType() {
        type = (type == VoteType.UP ? VoteType.DOWN : VoteType.UP);
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vote vote = (Vote) o;
        return Objects.equals(from, vote.from);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from);
    }
}

