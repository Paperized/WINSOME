package it.winsome.common.entity;

import it.winsome.common.SynchronizedObject;
import it.winsome.common.entity.abstracts.BaseVotableEntity;

/**
 * This represents a comment in the WINSOME social network
 * This entity can be synchronizable since it extends SynchronizedObject
 */
public class Comment extends BaseVotableEntity {
    private String owner;
    private String content;
    private int postId;
    boolean needIteration = true;

    public Comment() {
        this(-1);
    }

    public Comment(int id) {
        super(id);
    }

    public Comment(int id, String owner, String content) {
        super(id);
        this.owner = owner;
        this.content = content;
    }

    public String getOwner() {
        checkReadSynchronization();
        return owner;
    }

    public void setOwner(String owner) {
        checkWriteSynchronization();
        this.owner = owner;
    }

    public String getContent() {
        checkReadSynchronization();
        return content;
    }

    public void setContent(String content) {
        checkWriteSynchronization();
        this.content = content;
    }

    public int getPostId() {
        checkReadSynchronization();
        return postId;
    }

    public void setPostId(int postId) {
        checkWriteSynchronization();
        this.postId = postId;
    }

    @Override
    public String toString() {
        checkReadSynchronization();
        return "Comment{" +
                "id='" + getId() + '\'' +
                ", owner='" + owner + '\'' +
                ", content='" + content + '\'' +
                ", postId=" + postId + '\'' +
                ", creationDate=" + getCreationDate() +
                '}';
    }

    public boolean isNeedIteration() {
        checkReadSynchronization();
        return needIteration;
    }

    public void setNeedIteration(boolean needIteration) {
        checkWriteSynchronization();
        this.needIteration = needIteration;
    }

    @Override
    public <T extends SynchronizedObject> T enableSynchronization(boolean recursive) {
        super.enableSynchronization(recursive);
        if(recursive) {
            votesMap.forEach((k, v) -> v.enableSynchronization(true));
        }
        return (T) this;
    }
}
