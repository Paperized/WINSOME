package it.winsome.common.dto;

import it.winsome.common.entity.Post;
import it.winsome.common.network.NetMessage;
import it.winsome.common.WinsomeHelper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ShowFeedDTO {
    public int postCount;
    public List<Post> postList;

    public ShowFeedDTO() {
        postList = new ArrayList<>();
    }

    public ShowFeedDTO(int postCount) {
        this.postCount = postCount;
        postList = new ArrayList<>(postCount);
    }

    public Post getPost(int index) { return postList.get(index); }

    public static void netSerialize(NetMessage to, ShowFeedDTO feed) {
        to.writeCollection(feed.postList, ShowFeedDTO::netPostSerialize);
    }

    public static ShowFeedDTO netDeserialize(NetMessage from) {
        ShowFeedDTO feed = new ShowFeedDTO();
        from.readCollection(feed.postList, ShowFeedDTO::netPostDeserialize);
        feed.postCount = feed.postList.size();
        return feed;
    }

    public static int netSize(ShowFeedDTO feed) {
        return NetMessage.getCollectionSize(feed.postList, ShowFeedDTO::netPostSize);
    }

    public static void netPostSerialize(NetMessage to, Post post) {
        if(to.writeNullIfInvalid(post)) return;

        to.writeInt(post.getId())
            .writeString(post.getUsername());
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

    public static Post netPostDeserialize(NetMessage from) {
        if(from.isPeekingNull()) return null;

        Post post = new Post(from.readInt());
        post.setUsername(from.readString());

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

    public static int netPostSize(Post post) {
        if(post == null) return 4;

        return 24 // id 4, time 8, upvotes 4, downvote 4, comments 4
                + NetMessage.getStringSize(post.getUsername())
                // original null => 4 + title + content  o/w id 4 + time 8 + strings...
                + (post.getOriginalPost() == null ? 4 + NetMessage.getStringSize(post.getTitle(), post.getContent()) :
                    12 + NetMessage.getStringSize(post.getOriginalPost().getUsername(),
                        post.getOriginalPost().getTitle(),
                        post.getOriginalPost().getContent()));
    }
}
