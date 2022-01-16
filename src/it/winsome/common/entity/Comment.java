package it.winsome.common.entity;

import it.winsome.common.entity.abstracts.BaseVotableEntity;

public class Comment extends BaseVotableEntity {
    private String owner;
    private String content;
    private int postId;

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
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id='" + getId() + '\'' +
                ", owner='" + owner + '\'' +
                ", content='" + content + '\'' +
                ", postId=" + postId + '\'' +
                ", creationDate=" + getCreationDate() +
                '}';
    }
}
