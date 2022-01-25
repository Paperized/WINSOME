package it.winsome.common.entity.enums;

/**
 * Vote Type which can be positive or negative
 */
public enum VoteType {
    UP(0),
    DOWN(1);

    private int id;
    VoteType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static VoteType fromId(int id) {
        for (VoteType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }

    public static VoteType fromString(String vote) {
        if(vote.equals("+1")) {
            return UP;
        } else if(vote.equals("-1")) {
            return DOWN;
        }

        return null;
    }

    @Override
    public String toString() {
        if(id == UP.id) {
            return "+1";
        } else if(id == DOWN.id) {
            return "-1";
        }

        return null;
    }
}
