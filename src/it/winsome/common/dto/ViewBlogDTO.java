package it.winsome.common.dto;

import it.winsome.common.entity.Post;
import it.winsome.common.network.NetMessage;
import it.winsome.common.WinsomeHelper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * View blog data transfer
 */
public class ViewBlogDTO {
    public int postCount;
    public List<Post> postList;

    public ViewBlogDTO() {
        postList = new ArrayList<>();
    }

    public Post getPost(int index) { return postList.get(index); }

    public ViewBlogDTO(int postCount) {
        this.postCount = postCount;
        postList = new ArrayList<>(postCount);
    }

    public static void netSerialize(NetMessage to, ViewBlogDTO feed) {
        to.writeCollection(feed.postList, ViewBlogDTO::netPostSerialize);
    }

    public static ViewBlogDTO netDeserialize(NetMessage from) {
        ViewBlogDTO blog = new ViewBlogDTO();
        from.readCollection(blog.postList, ViewBlogDTO::netPostDeserialize);
        blog.postCount = blog.postList.size();
        return blog;
    }

    public static int netSize(ViewBlogDTO blog) {
        return NetMessage.getCollectionSize(blog.postList, ViewBlogDTO::netPostSize);
    }

    private static void netPostSerialize(NetMessage to, Post post) {
        if(to.writeNullIfInvalid(post)) return;

        to.writeInt(post.getId());
        if(post.isRewin()) {
            to.writeInt(post.getOriginalPost().getId())
                    .writeString(post.getOriginalPost().getUsername())
                    .writeString(post.getOriginalPost().getTitle())
                    .writeString(post.getOriginalPost().getContent())
                    .writeLong(post.getOriginalPost().getCreationDate().getTime());
        } else {
            to.writeNull()
                    .writeString(post.getTitle())
                    .writeString(post.getContent());
        }

        to.writeLong(post.getCreationDate().getTime());
        to.writeInt(post.getCommentCount())
                .writeInt(post.getTotalUpvotes())
                .writeInt(post.getTotalDownvotes());

        WinsomeHelper.printlnDebug(post.toString());
    }

    private static Post netPostDeserialize(NetMessage from) {
        if(from.isPeekingNull()) return null;

        Post post = new Post(from.readInt());
        int rewinId = from.readInt();
        if(rewinId != NetMessage.NULL_IDENTIFIER) {
            Post original = new Post(rewinId, from.readString(), from.readString(), from.readString());
            original.setCreationDate(Timestamp.from(Instant.ofEpochMilli(from.readLong())));
            post.setOriginalPost(original);
        } else {
            post.setTitle(from.readString());
            post.setContent(from.readString());
        }

        post.setCreationDate(Timestamp.from(Instant.ofEpochMilli(from.readLong())));
        post.setTotalComments(from.readInt());
        post.setTotalUpvotes(from.readInt());
        post.setTotalDownvotes(from.readInt());

        WinsomeHelper.printlnDebug(post.toString());
        return post;
    }

    private static int netPostSize(Post post) {
        if(post == null) return 4;

        return 24 // id 4, time 8, upvotes 4, downvote 4, comments 4
                // original null => 4 + title + content  o/w id 4 + time 8 + strings...
                + (post.getOriginalPost() == null ? 4 + NetMessage.getStringSize(post.getTitle(), post.getContent()) :
                12 + NetMessage.getStringSize(post.getOriginalPost().getUsername(),
                        post.getOriginalPost().getTitle(),
                        post.getOriginalPost().getContent()));
    }
}
