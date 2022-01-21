package it.winsome.common.entity;

import it.winsome.common.WinsomeHelper;

import java.io.Serializable;
import java.util.*;

public class User implements Serializable, Cloneable {
    private String username;
    private String password;
    private Set<String> tagsInterests;
    private Set<String> usersFollowed;
    private Set<String> usersFollowing;
    private boolean walletBTCupdated = true;
    private double walletSize;
    private double walletSizeBTC;

    public User() {
        tagsInterests = new HashSet<>();
        usersFollowed = new HashSet<>(0);
        usersFollowing = new HashSet<>(0);
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
        return usersFollowed.add(username.toLowerCase());
    }

    public boolean addUserFollowing(String username) {
        return usersFollowing.add(username.toLowerCase());
    }

    public boolean hasUserFollowed(String username) {
        return usersFollowed.contains(username.toLowerCase());
    }

    public boolean hasUserFollowing(String username) {
        return usersFollowing.contains(username.toLowerCase());
    }

    public boolean removeUserFollowed(String username) {
        return usersFollowed.remove(username.toLowerCase());
    }

    public boolean removeUserFollowing(String username) {
        return usersFollowing.remove(username.toLowerCase());
    }

    public boolean addTag(String tag) {
        return tagsInterests.add(WinsomeHelper.normalizeTag(tag));
    }

    public boolean hasTag(String tag) {
        return tagsInterests.contains(tag.toLowerCase());
    }

    public boolean hasSimilarTags(Collection<String> tags) {
        for(String tag : tags) {
            if(tagsInterests.contains(tag.toString())) {
                return true;
            }
        }

        return false;
    }

    public boolean hasAnyTag() {
        return tagsInterests.size() > 0;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Iterator<String> getFollowedIterator() {
        return usersFollowed.iterator();
    }

    public Set<String> getFollowed() {
        return Collections.unmodifiableSet(usersFollowed);
    }

    public Iterator<String> getTagsIterator() {
        return tagsInterests.iterator();
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tagsInterests);
    }

    public Iterator<String> getFollowingIterator() {
        return usersFollowing.iterator();
    }

    public Set<String> getFollowing() {
        return Collections.unmodifiableSet(usersFollowing);
    }

    public boolean isWalletBTCupdated() {
        return walletBTCupdated;
    }

    public double getWalletSizeBTC() {
        return walletSizeBTC;
    }

    public void setWalletSizeBTC(double walletSizeBTC) {
        this.walletSizeBTC = walletSizeBTC;
        walletBTCupdated = true;
    }

    public double addWalletAmount(double amount) {
        if(amount != 0) walletBTCupdated = false;
        return walletSize += amount;
    }

    public double getWalletSize() {
        return walletSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return username.equalsIgnoreCase(user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username.toLowerCase());
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        User user = (User) super.clone();
        user.tagsInterests = new HashSet<>(tagsInterests);
        user.usersFollowing = new HashSet<>(usersFollowing);
        user.usersFollowed = new HashSet<>(usersFollowed);
        return user;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", tagsInterests=" + tagsInterests +
                ", usersFollowed=" + usersFollowed +
                ", usersFollowing=" + usersFollowing +
                '}';
    }
}
