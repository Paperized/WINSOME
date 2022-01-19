package it.winsome.common.entity.abstracts;

import it.winsome.common.entity.enums.VoteType;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseVotableEntity extends BaseSocialEntity {
    private Map<String, VoteType> votesMap;
    private int totalUpvotes;
    private int totalDownvotes;

    public BaseVotableEntity() {
        this(0);
    }

    public BaseVotableEntity(int id) {
        super(id);
        votesMap = new HashMap<>(0);
    }

    public boolean addVote(String username, VoteType type) {
        VoteType prevVote = votesMap.putIfAbsent(username, type);

        if(prevVote == type) {
            totalUpvotes += (type == VoteType.UP ? 1 : -1);
            return true;
        }

        return false;
    }

    public VoteType getVote(String username) {
        return votesMap.get(username);
    }

    public boolean removeVote(String username) {
        VoteType removed = votesMap.remove(username);
        if(removed != null) {
            totalUpvotes += (removed == VoteType.UP ? -1 : 1);
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

    @Override
    public <T extends BaseSocialEntity> T deepCopyAs() {
        BaseVotableEntity base = super.deepCopyAs();
        base.votesMap = new HashMap<>(votesMap);
        return (T) base;
    }
}
