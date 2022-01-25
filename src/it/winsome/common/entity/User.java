package it.winsome.common.entity;

import it.winsome.common.WinsomeHelper;
import it.winsome.common.SynchronizedObject;
import it.winsome.common.entity.enums.CurrencyType;
import it.winsome.common.exception.SynchronizedInitException;

import java.io.Serializable;
import java.util.*;

/**
 * This represents a user in the social network, it contains also a wallet containing every transaction
 * This entity can be synchronizable since it extends SynchronizedObject
 */
public class User extends SynchronizedObject implements Serializable {
    private String username;
    private String password;
    private Set<String> tagsInterests;
    private Set<String> usersFollowed;
    private Set<String> usersFollowing;
    private Wallet wallet;

    public User() {
        tagsInterests = new HashSet<>();
        usersFollowed = new HashSet<>(0);
        usersFollowing = new HashSet<>(0);
        wallet = new Wallet();
    }

    public User(String username, String password) {
        this();
        this.username = username;
        this.password = password;
    }

    public static User fromUsername(String username) {
        return new User(username, "");
    }

    public User(String username, String password, String[] tags) {
        this(username, password);
        if(tags != null) {
            for (String tag : tags) {
                tagsInterests.add(WinsomeHelper.normalizeTag(tag));
            }
        }
    }

    public User(String username, String password, Set<String> tags) {
        this(username, password);
        if(tags != null) {
            for (String tag : tags) {
                tagsInterests.add(WinsomeHelper.normalizeTag(tag));
            }
        }
    }

    public User(String username, String password, Set<String> tags, Set<String> usersFollowing, Set<String> usersFollowed) {
        this.username = username;
        this.password = password;
        if(tags != null) {
            this.tagsInterests = new HashSet<>(tags.size());
            for (String tag : tags) {
                tagsInterests.add(WinsomeHelper.normalizeTag(tag));
            }
        } else {
            this.tagsInterests = new HashSet<>(0);
        }

        this.usersFollowing = usersFollowing != null ? usersFollowing : new HashSet<>(0);
        this.usersFollowed = usersFollowed != null ? usersFollowed : new HashSet<>(0);
    }

    public boolean addUserFollowed(String username) {
        checkWriteSynchronization();
        return usersFollowed.add(username.toLowerCase());
    }

    public boolean addUserFollowing(String username) {
        checkWriteSynchronization();
        return usersFollowing.add(username.toLowerCase());
    }

    public boolean hasUserFollowed(String username) {
        checkReadSynchronization();
        return usersFollowed.contains(username.toLowerCase());
    }

    public boolean hasUserFollowing(String username) {
        checkWriteSynchronization();
        return usersFollowing.contains(username.toLowerCase());
    }

    public boolean removeUserFollowed(String username) {
        checkWriteSynchronization();
        return usersFollowed.remove(username.toLowerCase());
    }

    public boolean removeUserFollowing(String username) {
        checkWriteSynchronization();
        return usersFollowing.remove(username.toLowerCase());
    }

    public boolean addTag(String tag) {
        checkWriteSynchronization();
        return tagsInterests.add(WinsomeHelper.normalizeTag(tag));
    }

    public boolean hasTag(String tag) {
        checkReadSynchronization();
        return tagsInterests.contains(tag.toLowerCase());
    }

    public boolean hasSimilarTags(Collection<String> tags) {
        checkReadSynchronization();
        for(String tag : tags) {
            if(tagsInterests.contains(tag)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasAnyTag() {
        checkReadSynchronization();
        return tagsInterests.size() > 0;
    }

    public String getUsername() {
        checkReadSynchronization();
        return username;
    }

    public void setUsername(String username) {
        checkWriteSynchronization();
        this.username = username;
    }

    public String getPassword() {
        checkReadSynchronization();
        return password;
    }

    public void setPassword(String password) {
        checkWriteSynchronization();
        this.password = password;
    }

    public Iterator<String> getFollowedIterator() {
        checkReadSynchronization();
        return usersFollowed.iterator();
    }

    public Set<String> getFollowed() {
        checkReadSynchronization();
        return Collections.unmodifiableSet(usersFollowed);
    }

    public Iterator<String> getTagsIterator() {
        checkReadSynchronization();
        return tagsInterests.iterator();
    }

    public Set<String> getTags() {
        checkReadSynchronization();
        return Collections.unmodifiableSet(tagsInterests);
    }

    public Iterator<String> getFollowingIterator() {
        checkReadSynchronization();
        return usersFollowing.iterator();
    }

    public Set<String> getFollowing() {
        checkReadSynchronization();
        return Collections.unmodifiableSet(usersFollowing);
    }

    public Wallet getWallet() {
        checkReadSynchronization();
        return wallet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;

        checkReadSynchronization();
        return username.equalsIgnoreCase(user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username.toLowerCase());
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        checkReadSynchronization();
        User user = (User) super.clone();
        try {
            user.initSynchronizedObject();
        } catch (SynchronizedInitException e) {
            e.printStackTrace();
            return null;
        }

        return user;
    }

    public User deepCopy() {
        checkReadSynchronization();
        try {
            User user = (User) cloneAndResetSynchronizer();
            user.tagsInterests = new HashSet<>(tagsInterests);
            user.usersFollowing = new HashSet<>(usersFollowing);
            user.usersFollowed = new HashSet<>(usersFollowed);

            wallet.prepareRead();
            user.wallet = wallet.deepCopyByCurrency(CurrencyType.Winsome);
            wallet.releaseRead();

            return user;
        } catch(CloneNotSupportedException e) { return null; }
    }

    @Override
    public String toString() {
        checkReadSynchronization();
        return "User{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", tagsInterests=" + tagsInterests +
                ", usersFollowed=" + usersFollowed +
                ", usersFollowing=" + usersFollowing +
                '}';
    }

    @Override
    public <T extends SynchronizedObject> T enableSynchronization(boolean recursive) {
        super.enableSynchronization(recursive);
        if(recursive) {
            wallet.enableSynchronization(true);
        }

        return (T) this;
    }
}
