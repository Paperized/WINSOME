package it.winsome.common.entity.enums;

/**
 * Types of currency currently supported by WINSOME
 */
public enum CurrencyType {
    Winsome(0),
    Bitcoin(1);

    private final int id;
    CurrencyType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static CurrencyType fromId(int id) {
        for (CurrencyType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }

    public static CurrencyType fromString(String str) {
        if(str.equalsIgnoreCase("winsome")) {
            return Winsome;
        } else if(str.equalsIgnoreCase("btc")) {
            return Bitcoin;
        }

        return null;
    }

    @Override
    public String toString() {
        if(id == Winsome.getId()) {
            return "winsome";
        } else if(id == Bitcoin.getId()) {
            return "btc";
        }

        return "";
    }
}
