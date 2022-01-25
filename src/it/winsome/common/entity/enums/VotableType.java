package it.winsome.common.entity.enums;

/**
 * Votable Types of entities
 */
public enum VotableType {
    Comment(0),
    Post(1);

    private int id;
    VotableType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static VotableType fromId(int id) {
        for (VotableType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        if(id == Comment.getId()) {
            return "comment";
        } else if(id == Post.getId()) {
            return "post";
        }

        return "";
    }
}
