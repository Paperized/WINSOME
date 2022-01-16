package it.winsome.common.dto;

import it.winsome.common.entity.Comment;
import it.winsome.common.entity.Post;
import it.winsome.common.network.NetMessage;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;

public class ShowPostDTO {
    public Post post;

    public ShowPostDTO() { }
    public ShowPostDTO(Post post) { this.post = post; }

    public static void netSerialize(NetMessage to, ShowPostDTO feed) {
        to.writeObject(feed.post, ShowPostDTO::netPostSerialize);
    }

    public static ShowPostDTO netDeserialize(NetMessage from) {
        ShowPostDTO dto = new ShowPostDTO();
        dto.post = from.readObject(ShowPostDTO::netPostDeserialize);
        return dto;
    }

    public static void netPostSerialize(NetMessage to, Post post) {
        if(to.writeNullIfInvalid(post)) return;

        to.writeInt(post.getId());
        to.writeString(post.getUsername());
        if(post.isRewin()) {
            to.writeInt(post.getOriginalPost().getId());
            to.writeString(post.getOriginalPost().getUsername());
            to.writeString(post.getOriginalPost().getTitle());
            to.writeString(post.getOriginalPost().getContent());
            to.writeLong(post.getOriginalPost().getCreationDate().getTime());
        } else {
            to.writeNull();
            to.writeString(post.getTitle());
            to.writeString(post.getContent());
        }

        to.writeLong(post.getCreationDate().getTime());
        to.writeInt(post.getTotalUpvotes());
        to.writeInt(post.getTotalDownvotes());
        to.writeCollection(post.getComments(), ShowPostDTO::netCommentSerialize);
    }

    public static Post netPostDeserialize(NetMessage from) {
        if(from.isPeekingNull()) return null;

        Post post = new Post(from.readInt());
        post.setUsername(from.readString());
        int rewinId = from.readInt();
        if(rewinId != NetMessage.NULL_IDENTIFIER) {
            Post rewin = new Post(rewinId, from.readString(), from.readString(), from.readString());
            rewin.setCreationDate(Timestamp.from(Instant.ofEpochMilli(from.readLong())));
            post.setOriginalPost(rewin);
        } else {
            post.setTitle(from.readString());
            post.setContent(from.readString());
        }

        post.setCreationDate(Timestamp.from(Instant.ofEpochMilli(from.readLong())));
        post.setTotalUpvotes(from.readInt());
        post.setTotalDownvotes(from.readInt());

        Collection<Comment> comments = new ArrayList<>();
        from.readCollection(comments, ShowPostDTO::netCommentDeserialize);
        post.setComments(comments);
        return post;
    }

    public static void netCommentSerialize(NetMessage to, Comment comment) {
        if(to.writeNullIfInvalid(comment)) return;

        to.writeInt(comment.getId());
        to.writeString(comment.getOwner());
        to.writeString(comment.getContent());
        to.writeLong(comment.getCreationDate().getTime());
        to.writeInt(comment.getTotalUpvotes());
        to.writeInt(comment.getTotalDownvotes());
    }

    public static Comment netCommentDeserialize(NetMessage from) {
        if(from.isPeekingNull()) return null;

        Comment comment = new Comment(from.readInt(), from.readString(), from.readString());
        comment.setCreationDate(Timestamp.from(Instant.ofEpochMilli(from.readLong())));
        comment.setTotalUpvotes(from.readInt());
        comment.setTotalDownvotes(from.readInt());
        return comment;
    }

    public static int netCommentSize(Comment comment) {
        if(comment == null) return 4;

        return 20 + NetMessage.getStringSize(comment.getOwner(), comment.getContent());
    }

    public static int netPostSize(Post post) {
        if(post == null) return 4;

        return 20 // id 4, time 8, upvote 4, downvote 4
                + NetMessage.getStringSize(post.getUsername())
                + (post.isRewin() ? 12 + NetMessage.getStringSize(post.getOriginalPost().getUsername(),
                                                                post.getOriginalPost().getTitle(),
                                                                post.getOriginalPost().getContent()) :
                                    NetMessage.getStringSize(post.getTitle(), post.getContent()))
                + NetMessage.getCollectionSize(post.getComments(), ShowPostDTO::netCommentSize);
    }

    public static int netSize(ShowPostDTO showPost) {
        return netPostSize(showPost.post);
    }
}
