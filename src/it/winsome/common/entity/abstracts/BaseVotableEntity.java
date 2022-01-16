package it.winsome.common.entity.abstracts;

import it.winsome.common.entity.Vote;
import it.winsome.common.entity.enums.VoteType;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseVotableEntity extends BaseSocialEntity {
    private final Map<String, Vote> votesMap;
    private int totalUpvotes;
    private int totalDownvotes;

    public BaseVotableEntity() {
        this(0);
    }

    public BaseVotableEntity(int id) {
        super(id);
        votesMap = new HashMap<>(0);
    }

    public Vote addVote(String username, VoteType type) {
        Vote newVote = new Vote(username, type);
        Vote prevVote = votesMap.putIfAbsent(username, newVote);

        if(prevVote == newVote) {
            totalUpvotes += (newVote.getType() == VoteType.UP ? 1 : -1);
            return newVote;
        }

        return null;
    }

    public Vote getVote(String username) {
        return votesMap.get(username);
    }

    public boolean removeVote(String username) {
        Vote removed = votesMap.remove(username);
        if(removed != null) {
            totalUpvotes += (removed.getType() == VoteType.UP ? -1 : 1);
            return true;
        }

        return false;
    }

    public int getTotalUpvotes() {
        return totalUpvotes;
    }

    public void setTotalUpvotes(int totalUpvotes) {
        this.totalUpvotes = totalUpvotes;
    }

    public int getTotalDownvotes() {
        return totalDownvotes;
    }

    public void setTotalDownvotes(int totalDownvotes) {
        this.totalDownvotes = totalDownvotes;
    }
}
