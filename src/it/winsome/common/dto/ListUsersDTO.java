package it.winsome.common.dto;

import it.winsome.common.entity.Post;
import it.winsome.common.entity.User;
import it.winsome.common.network.NetMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ListUsersDTO {
    public final List<User> userList;

    public ListUsersDTO() { userList = new ArrayList<>(); }
    public ListUsersDTO(List<User> users) {
        userList = users;
    }

    public static void netSerialize(NetMessage to, ListUsersDTO list) {
        to.writeCollection(list.userList, ListUsersDTO::netUserSerialize);
    }

    public static ListUsersDTO netDeserialize(NetMessage from) {
        ListUsersDTO list = new ListUsersDTO();
        from.readCollection(list.userList, ListUsersDTO::netUserDeserialize);
        return list;
    }

    public static int netSize(ListUsersDTO list) {
        return NetMessage.getCollectionSize(list.userList, ListUsersDTO::netUserSize);
    }

    public static void netUserSerialize(NetMessage to, User user) {
        if(to.writeNullIfInvalid(user)) return;

        to.writeString(user.getUsername())
                .writeCollection(user.getTags());
    }

    public static User netUserDeserialize(NetMessage from) {
        if(from.isPeekingNull()) return null;

        String username = from.readString();
        Set<String> tags = new HashSet<>();
        from.readCollection(tags);
        return new User(username, "", tags);
    }

    public static int netUserSize(User user) {
        if(user == null) return 4;
        return NetMessage.getStringSize(user.getUsername()) + NetMessage.getCollectionSize(user.getTags());
    }
}
