package it.winsome.common.entity.abstracts;

import it.winsome.common.entity.Vote;
import it.winsome.common.entity.enums.VoteType;

import java.util.*;

public abstract class BaseVotableEntity extends BaseSocialEntity {
    private Map<String, Vote> votesMap;
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
        if(votesMap.containsKey(username)) {
            return false;
        }

        votesMap.put(username, new Vote(username, type));
        return true;
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

    public Collection<Vote> getVotes() { return Collections.unmodifiableCollection(votesMap.values()); }

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
