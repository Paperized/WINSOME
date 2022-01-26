package it.winsome.common.entity.abstracts;

import it.winsome.common.entity.Vote;
import it.winsome.common.entity.enums.VoteType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class for votable entities, it extends from BaseSocialEntity then it is also
 * synchronizable.
 * This class offers a way to vote (like/dislike) an existing entity
 */
public abstract class BaseVotableEntity extends BaseSocialEntity {
    protected Map<String, Vote> votesMap;
    private int totalUpvotes;
    private int totalDownvotes;

    public BaseVotableEntity() {
        this(0);
    }

    public BaseVotableEntity(int id) {
        super(id);
        votesMap = new HashMap<>(0);
    }

    public boolean addVote(Vote vote) {
        checkWriteSynchronization();
        if(votesMap.containsKey(vote.getFrom())) {
            return false;
        }

        votesMap.put(vote.getFrom(), vote);
        if(vote.getType() == VoteType.UP) {
            totalUpvotes++;
        } else {
            totalDownvotes--;
        }
        return true;
    }

    public Vote getVote(String username) {
        checkReadSynchronization();
        return votesMap.get(username);
    }

    public Collection<Vote> getVotes() { return Collections.unmodifiableCollection(votesMap.values()); }

    public int getTotalUpvotes() {
        checkReadSynchronization();
        return totalUpvotes;
    }

    public void setTotalUpvotes(int totalUpvotes) {
        checkWriteSynchronization();
        this.totalUpvotes = totalUpvotes;
    }

    public int getTotalDownvotes() {
        checkReadSynchronization();
        return totalDownvotes;
    }

    public void setTotalDownvotes(int totalDownvotes) {
        checkWriteSynchronization();
        this.totalDownvotes = totalDownvotes;
    }

    @Override
    public <T extends BaseSocialEntity> T deepCopyAs() {
        BaseVotableEntity base = super.deepCopyAs();
        base.votesMap = votesMap.values().stream().map(x -> {
            x.prepareRead();
            Vote dc = new Vote(x.getFrom(), x.getType());
            dc.setNeedIteration(x.isNeedIteration());
            x.releaseRead();
            return dc;
        }).collect(Collectors.toMap(Vote::getFrom, y->y));
        return (T) base;
    }
}
