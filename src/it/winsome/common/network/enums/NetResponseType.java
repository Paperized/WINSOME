package it.winsome.common.network.enums;

public enum NetResponseType {
    Success(0),
    InvalidParameters(1),
    Unknown(2),
    UsernameNotExists(3),
    WrongPassword(4),
    ClientNotLoggedIn(5),
    ClientAlreadyLoggedIn(6),
    UserSelfFollow(7),
    UserNotFollowed(8),
    OriginalPostNotExists(9),
    NotAuthorized(10),
    UserSelfVote(11),
    UserAlreadyVoted(12),
    PostNotInFeed(13),
    UserSelfComment(14),
    UserSelfRewin(15),
    EntityNotExists(16),
    UserAlreadyLoggedIn(17);

    private int id;
    NetResponseType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static NetResponseType fromId(int id) {
        for (NetResponseType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }
}
