package it.winsome.common.entity;

import com.google.gson.annotations.JsonAdapter;
import it.winsome.common.entity.abstracts.BaseSocialEntity;
import it.winsome.common.entity.abstracts.BaseVotableEntity;
import it.winsome.common.json.PostIdJsonAdapter;

import java.util.*;
import java.util.stream.Collectors;

public class Post extends BaseVotableEntity {
    private String username;
    private String title;
    private String content;

    @JsonAdapter(PostIdJsonAdapter.class)
    private Post originalPost;
    private Map<Comment, Comment> comments;
    private int totalComments;

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
        if(comment == null) return false;
        Comment added = comments.putIfAbsent(comment, comment);
        if(added != null && !added.equals(comment)) {
            totalComments++;
            return true;
        }

        return false;
    }

    public Comment hasComment(Comment comment) {
        return comments.get(comment);
    }

    public Set<Comment> getComments() { return Collections.unmodifiableSet(comments.keySet()); }

    public Comment removeComment(Comment comment) {
        Comment removed = comments.remove(comment);
        if(removed != null)
            totalComments--;
        return removed;
    }

    public void setComments(Collection<Comment> comments) {
        if(comments == null) throw new NullPointerException();
        // map each of them in the map
        this.comments = comments.stream().collect(Collectors.toMap(x -> x, x -> x));
        totalComments = comments.size();
    }

    public int getCommentCount() { return comments.size(); }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isRewin() {
        return originalPost != null;
    }

    public Post getOriginalPost() {
        return originalPost;
    }

    public int getTotalComments() {
        return totalComments;
    }

    public void setTotalComments(int totalComments) {
        this.totalComments = totalComments;
    }

    public void setOriginalPost(Post originalPost) {
        boolean originalFound = false;
        Post currentPost = originalPost;
        while(currentPost != null) {
            if(currentPost.originalPost == null) {
                originalFound = true;
                break;
            }

            currentPost = currentPost.originalPost;
        }

        if(!originalFound)
            throw new IllegalArgumentException("No original post found during the walkthrough!");
        this.originalPost = originalPost;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        Post post = (Post) super.clone();
        post.comments = new HashMap<>(comments);
        post.originalPost = (Post) originalPost.clone();
        return post;
    }

    @Override
    public <T extends BaseSocialEntity> T deepCopyAs() {
        Post post = super.deepCopyAs();
        post.comments = comments.values().stream().map(Comment::<Comment>deepCopyAs)
                .collect(Collectors.toMap(x->x, y->y));
        if(originalPost != null)
            post.originalPost = originalPost.deepCopyAs();
        return (T) post;
    }

    @Override
    public String toString() {
        return "Post{" +
                "id='" + getId() + '\'' +
                ", username='" + username + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", originalPost=" + originalPost + '\'' +
                ", creationDate=" + getCreationDate() +
                '}';
    }


}
