package it.winsome.common.entity;

import com.google.gson.annotations.JsonAdapter;
import it.winsome.common.SynchronizedObject;
import it.winsome.common.WinsomeHelper;
import it.winsome.common.entity.abstracts.BaseSocialEntity;
import it.winsome.common.entity.abstracts.BaseVotableEntity;
import it.winsome.common.json.PostIdJsonAdapter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This represents a post inside the social network, it includes a list of comments and eventually
 * a "rewinned" post.
 * This entity can be synchronizable since it extends SynchronizedObject
 */
public class Post extends BaseVotableEntity {
    private String username;
    private String title;
    private String content;

    @JsonAdapter(PostIdJsonAdapter.class)
    private Post originalPost;
    private Map<Comment, Comment> comments;
    private int totalComments;
    int currentIteration = 0;

    public Post() {
        this(-1);
    }

    public Post(int id) {
        super(id);
        comments = new LinkedHashMap<>();
    }

    public Post(int id, String username, String title, String content, Map<Comment, Comment> comments) {
        super(id);
        this.username = username;
        this.title = title;
        this.content = content;
        this.comments = comments != null ? comments : new HashMap<>(0);
    }

    public Post(int id, String username, String title, String content) {
        this(id, username, title, content, null);
    }

    public boolean addComment(Comment comment) {
        checkWriteSynchronization();
        if(comment == null) return false;
        if(comments.containsKey(comment))
            return false;

        comments.put(comment, comment);
        totalComments++;
        return true;
    }

    public Set<Comment> getComments() {
        checkReadSynchronization();
        return Collections.unmodifiableSet(comments.keySet());
    }

    public void setComments(Collection<Comment> comments) {
        checkWriteSynchronization();
        if(comments == null) throw new NullPointerException();
        // map each of them in the map
        this.comments = comments.stream().collect(Collectors.toMap(x -> x, x -> x));
        totalComments = comments.size();
    }

    public int getCommentCount() {
        checkReadSynchronization();
        return comments.size();
    }

    public String getUsername() {
        checkReadSynchronization();
        return username;
    }

    public void setUsername(String username) {
        checkWriteSynchronization();
        this.username = username;
    }

    public String getTitle() {
        checkReadSynchronization();
        return title;
    }

    public void setTitle(String title) {
        checkWriteSynchronization();
        this.title = title;
    }

    public String getContent() {
        checkReadSynchronization();
        return content;
    }

    public void setContent(String content) {
        checkWriteSynchronization();
        this.content = content;
    }

    public boolean isRewin() {
        checkReadSynchronization();
        return originalPost != null;
    }

    public Post getOriginalPost() {
        checkReadSynchronization();
        return originalPost;
    }

    public int getTotalComments() {
        checkReadSynchronization();
        return totalComments;
    }

    public void setTotalComments(int totalComments) {
        checkWriteSynchronization();
        this.totalComments = totalComments;
    }

    public void setOriginalPost(Post originalPost) {
        if(originalPost == null) return;
        checkWriteSynchronization();
        Post currentPost = originalPost;
        while (currentPost.originalPost != null) {
            currentPost = currentPost.originalPost;
        }

        this.originalPost = originalPost;
    }

    @Override
    public <T extends BaseSocialEntity> T deepCopyAs() {
        Post post = super.deepCopyAs();
        post.comments = WinsomeHelper.deepCopySynchronizedMap(comments.values().stream(), x->x, y->y);

        if(originalPost != null) {
            originalPost.prepareRead();
            post.originalPost = originalPost.deepCopyAs();
            originalPost.releaseRead();
        }

        return (T) post;
    }

    @Override
    public String toString() {
        checkReadSynchronization();
        return "Post{" +
                "id='" + getId() + '\'' +
                ", username='" + username + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", originalPost=" + originalPost + '\'' +
                ", creationDate=" + getCreationDate() +
                '}';
    }

    public int getCurrentIteration() {
        checkReadSynchronization();
        return currentIteration;
    }

    public void setCurrentIteration(int currentIteration) {
        checkWriteSynchronization();
        this.currentIteration = currentIteration;
    }

    @Override
    public <T extends SynchronizedObject> T enableSynchronization(boolean recursive) {
        super.enableSynchronization(recursive);
        if(recursive) {
            if(originalPost != null)
                originalPost.enableSynchronization(true);

            comments.forEach((k, v) -> v.enableSynchronization(true));
            votesMap.forEach((k, v) -> v.enableSynchronization(true));
        }
        return (T) this;
    }
}
