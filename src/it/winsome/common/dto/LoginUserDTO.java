package it.winsome.common.dto;

import it.winsome.common.entity.User;
import it.winsome.common.network.NetMessage;

import java.util.HashSet;
import java.util.Set;

public class LoginUserDTO {
    public User user;

    public LoginUserDTO() {  }
    public LoginUserDTO(User user) {
        this.user = user;
    }

    public static void netSerialize(NetMessage to, LoginUserDTO dto) {
        to.writeObject(dto.user, LoginUserDTO::netUserSerialize);
    }

    public static LoginUserDTO netDeserialize(NetMessage from) {
        LoginUserDTO list = new LoginUserDTO();
        list.user = from.readObject(LoginUserDTO::netUserDeserialize);
        return list;
    }

    public static int netSize(LoginUserDTO list) {
        return netUserSize(list.user);
    }

    public static void netUserSerialize(NetMessage to, User user) {
        if(to.writeNullIfInvalid(user)) return;

        to.writeCollection(user.getTags())
                .writeCollection(user.getFollowing())
                .writeCollection(user.getFollowed());
    }

    public static User netUserDeserialize(NetMessage from) {
        if(from.isPeekingNull()) return null;

        Set<String> tags = new HashSet<>();
        from.readCollection(tags);
        Set<String> following = new HashSet<>();
        from.readCollection(following);
        Set<String> followed = new HashSet<>();
        from.readCollection(followed);
        return new User("", "", tags, following, followed);
    }

    public static int netUserSize(User user) {
        if(user == null) return 4;
        return NetMessage.getCollectionSize(user.getTags()) +
                NetMessage.getCollectionSize(user.getFollowing()) +
                NetMessage.getCollectionSize(user.getFollowed());
    }
}
