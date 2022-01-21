package it.winsome.common.network.enums;

public enum NetMessageType {
    None(0),
    Login(1),
    Logout(2),
    Follow(3),
    Unfollow(4),
    ListUser(5),
    CreatePost(6),
    DeletePost(7),
    ShowPost(8),
    ShowFeed(9),
    RewinPost(10),
    RatePost(11),
    RateComment(12),
    CreateComment(13),
    ViewBlog(14),
    NotifyWallet(15);

    private final int id;
    NetMessageType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static NetMessageType fromId(int id) {
        for (NetMessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }

        return None;
    }
}
