package it.winsome.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import it.winsome.common.WinsomeHelper;
import it.winsome.common.entity.Comment;
import it.winsome.common.entity.Post;
import it.winsome.common.entity.User;
import it.winsome.common.entity.abstracts.BaseSocialEntity;
import it.winsome.common.entity.enums.VotableType;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.exception.*;
import it.winsome.common.json.JsonDeserializeEmptyMap;
import it.winsome.common.json.JsonSerializeMapIdOnly;
import it.winsome.common.network.enums.NetResponseType;
import it.winsome.common.service.interfaces.UserService;
import it.winsome.common.service.interfaces.UserCallback;

import java.io.*;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static it.winsome.common.network.enums.NetResponseType.*;

public class UserServiceImpl extends UnicastRemoteObject implements UserService {
    private final Map<String, User> registeredUsers;
    private final ReadWriteLock registeredUsersRW;
    private final Map<String, UserCallback> registeredCallbacks;
    private final Map<String, SelectionKey> currentSessions;
    private final ReadWriteLock currentSessionsRW;
    private final Map<String, LinkedList<Post>> cachedBlogs;
    private final ReadWriteLock cachedBlogsRW;

    private final AtomicInteger maxPostId;
    private final Map<Post, Post> postMap;
    private final ReadWriteLock postMapRW;
    private final AtomicInteger maxCommentId;
    private final Map<Integer, Comment> commentMap;
    private final ReadWriteLock commentMapRW;

    private final String dataFolder;

    public UserServiceImpl(String dataFolder) throws RemoteException {
        super();
        this.dataFolder = dataFolder;
        registeredCallbacks = new HashMap<>();
        registeredUsers = new HashMap<>();
        postMap = new LinkedHashMap<>();
        commentMap = new HashMap<>();
        currentSessions = new HashMap<>();
        cachedBlogs = new HashMap<>();

        maxPostId = new AtomicInteger();
        maxCommentId = new AtomicInteger();

        registeredUsersRW = new ReentrantReadWriteLock();
        cachedBlogsRW = new ReentrantReadWriteLock();
        postMapRW = new ReentrantReadWriteLock();
        commentMapRW = new ReentrantReadWriteLock();
        currentSessionsRW = new ReentrantReadWriteLock();

        if(loadFromDisk()) {
            WinsomeHelper.printlnDebug("Data loaded succesfully!");
        } else {
            WinsomeHelper.printlnDebug("Error during data loading, partial data might be loaded!");
        }
    }

    public synchronized boolean saveToDisk() {
        boolean allCompleted = true;
        Gson gsonPost = new GsonBuilder()
                .registerTypeAdapter(Map.class, new JsonSerializeMapIdOnly<Comment>())
                .setPrettyPrinting()
                .create();

        Lock rLock = WinsomeHelper.acquireReadLock(postMapRW);
        String jsonPost = gsonPost.toJson(postMap.values());
        File postFile = new File(dataFolder + "posts.json");
        try {
            postFile.getParentFile().mkdirs();
            postFile.createNewFile();
            try (FileOutputStream oFile = new FileOutputStream(postFile, false)) {
                oFile.write(jsonPost.getBytes(StandardCharsets.UTF_8));
            } catch (FileNotFoundException e) {
                // Should never reach here
                WinsomeHelper.printlnDebug("Post file not found!");
                allCompleted = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            allCompleted = false;
        }
        rLock.unlock();

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        rLock = WinsomeHelper.acquireReadLock(commentMapRW);
        String jsonComment = gson.toJson(commentMap.values());
        File commentFile = new File(dataFolder + "comments.json");
        try {
            commentFile.createNewFile();
            try (FileOutputStream oFile = new FileOutputStream(commentFile, false)) {
                oFile.write(jsonComment.getBytes(StandardCharsets.UTF_8));
            } catch (FileNotFoundException e) {
                // Should never reach here
                WinsomeHelper.printlnDebug("Comment file not found!");
                allCompleted = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            allCompleted = false;
        }
        rLock.unlock();

        rLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        String jsonUser = gson.toJson(registeredUsers.values());
        File userFile = new File(dataFolder + "users.json");
        try {
            userFile.createNewFile();
            try (FileOutputStream oFile = new FileOutputStream(userFile, false)) {
                oFile.write(jsonUser.getBytes(StandardCharsets.UTF_8));
            } catch (FileNotFoundException e) {
                // Should never reach here
                WinsomeHelper.printlnDebug("User file not found!");
                allCompleted = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            allCompleted = false;
        }
        rLock.unlock();

        return allCompleted;
    }

    public synchronized boolean loadFromDisk() {
        boolean allCompleted = true;
        Gson gsonPost = new GsonBuilder()
                .registerTypeAdapter(Map.class, new JsonDeserializeEmptyMap<Comment, Comment>())
                .create();

        try {
            String jsonPost = new String(Files.readAllBytes(Paths.get(dataFolder + "posts.json")), StandardCharsets.UTF_8);
            List<Post> posts = gsonPost.fromJson(jsonPost, new TypeToken<List<Post>>(){}.getType());
            posts.sort((o1, o2) -> o2.getCreationDate().compareTo(o1.getCreationDate()));
            int maxPostIdTemp = 0;
            for(Post post : posts) {
                postMap.put(post, post);
                maxPostIdTemp = Math.max(maxPostIdTemp, post.getId());
            }
            maxPostId.set(maxPostIdTemp);

            postMap.values().forEach(x -> {
                if(x.isRewin()) {
                    x.setOriginalPost(postMap.get(x.getOriginalPost()));
                }
            });
        } catch (IOException e) {
            if(!(e instanceof NoSuchFileException)) {
                e.printStackTrace();
                allCompleted = false;
            }
        }

        Gson gson = new GsonBuilder().create();

        try {
            String jsonComment = new String(Files.readAllBytes(Paths.get(dataFolder + "comments.json")), StandardCharsets.UTF_8);
            List<Comment> comments = gson.fromJson(jsonComment, new TypeToken<List<Comment>>(){}.getType());
            Post temp = new Post();
            int maxCommentIdTemp = 0;
            for(Comment comment : comments) {
                temp.setId(comment.getPostId());
                Post referredPost = postMap.get(temp);
                if(referredPost == null) {
                    WinsomeHelper.printfDebug("WARNING Load from disk! Comment with id %d set with post id %d but it does not exists!");
                    continue;
                }
                referredPost.addComment(comment);
                commentMap.put(comment.getId(), comment);
                maxCommentIdTemp = Math.max(maxCommentIdTemp, comment.getId());
            }
            maxCommentId.set(maxCommentIdTemp);
        } catch (IOException e) {
            if(!(e instanceof NoSuchFileException)) {
                e.printStackTrace();
                allCompleted = false;
            }
        }

        try {
            String jsonUser = new String(Files.readAllBytes(Paths.get(dataFolder + "users.json")), StandardCharsets.UTF_8);
            List<User> users = gson.fromJson(jsonUser, new TypeToken<List<User>>(){}.getType());
            for(User user : users) {
                registeredUsers.put(user.getUsername(), user);
            }
        } catch (IOException e) {
            if(!(e instanceof NoSuchFileException)) {
                e.printStackTrace();
                allCompleted = false;
            }
        }

        return allCompleted;
    }

    public void test() {
        if(registeredUsers.size() > 0) return;
        String psw = WinsomeHelper.generateFromSHA256("download99");
        registeredUsers.put("ghfjy787", new User("ghfjy787", psw, new String[] { "pesca", "programmazione", "film", "LoL", "youtube", "anime", "manga" }));
        cachedBlogs.put("ghfjy787", new LinkedList<>());
        registeredUsers.put("pippo99", new User("pippo99", psw, new String[] { "LoL", "agricoltura", "vino", "manga" }));
        cachedBlogs.put("pippo99", new LinkedList<>());
        registeredUsers.put("gianmarco", new User("gianmarco", psw, new String[] { "Film", "programmazione", "LoL", "youtube" }));
        cachedBlogs.put("gianmarco", new LinkedList<>());
        registeredUsers.put("piero", new User("piero", psw, new String[] { "crypto", "youtube", "film" }));
        cachedBlogs.put("piero", new LinkedList<>());
        registeredUsers.put("clacla", new User("clacla", psw, new String[] { "anime", "manga", "un cazzo" }));
        cachedBlogs.put("clacla", new LinkedList<>());

        registeredUsers.get("piero").addUserFollowed("ghfjy787");
        registeredUsers.get("piero").addUserFollowed("gianmarco");

        Post p = new Post(-1, "gianmarco", "Youtube è meglio delle crypto!", "YOUTUBE REGNA POGGGGGGGGG XD");
        addPost(p);
        Comment c = new Comment(-1, "piero", "Bel post niente da dire");
        c.setPostId(p.getId());
        c.addVote("ghfjy787", VoteType.UP);
        addComment(c, registeredUsers.get("piero"));
        c = new Comment(-1, "ghfjy787", "Non saprei meh..");
        c.setPostId(p.getId());
        c.addVote("piero", VoteType.UP);
        addComment(c, registeredUsers.get("ghfjy787"));
        c = new Comment(-1, "piero", "Bel post niente da dire");
        c.addVote("ghfjy787", VoteType.UP);
        addComment(c, registeredUsers.get("piero"));
        Post rewin = new Post(-1, "piero", "GIANMARCO MA COSA DICI PORCODDIOOOOO!", "VA IN MONA CO YOUTUBE!");
        rewin.setOriginalPost(new Post(p.getId()));
        addPost(rewin);
        addPost(new Post(-1, "piero", "Ecco perchè le monete virtuali sono OP AF!", "Allora praticamente I BTC sono la vostra salvezza\n, dovete comprarne all'infinito e basta.. XD"));
        p = new Post(-1, "piero", "BITCOIN SU FORTNITE!!!", "Pazzesco io boh, sicuramente comprero il panino di Trevis COTT!");
        addPost(p);
        c = new Comment(-1, "piero", "Bel post niente da dire");
        c.setPostId(p.getId());
        c.addVote("ghfjy787", VoteType.UP);
        addComment(c, registeredUsers.get("piero"));
        c = new Comment(-1, "piero", "Bel post niente da dire");
        c.addVote("ghfjy787", VoteType.UP);
        addComment(c, registeredUsers.get("piero"));
        c = new Comment(-1, "piero", "Bel post niente da dire");
        c.addVote("ghfjy787", VoteType.UP);
        addComment(c, registeredUsers.get("piero"));
        addPost(new Post(-1, "ghfjy787", "IDRATARSI BENE", "HO SETEEEEEE"));
        rewin = new Post(-1, "ghfjy787", "Pierino ancora una volta dice cagate!", null);
        rewin.setOriginalPost(new Post(p.getId()));
        addPost(rewin);
        addPost(new Post(-1, "pippo99", "A me mi piacciono le api grandi!", "sisi miele si!"));
        addPost(new Post(-1, "clacla", "Ecco perchè mi piaccono le anime TDDY!", "BEH LO SAPETE ANCHE VOI!"));

        WinsomeHelper.printfDebug("Max id post: %d", maxPostId.get());
        saveToDisk();
    }

    @Override
    public User registerUser(String username, String password, String[] tags) throws RemoteException, UserAlreadyExistsException, NoTagsFoundException {
        username = WinsomeHelper.normalizeUsername(username);
        User user = new User(username, password, tags);
        if(!user.hasAnyTag()) {
            throw new NoTagsFoundException();
        }

        Lock wLock = WinsomeHelper.acquireWriteLock(registeredUsersRW);
        User oldUser = registeredUsers.putIfAbsent(username, user);
        wLock.unlock();
        if(oldUser != null) {
            throw new UserAlreadyExistsException();
        }

        return user;
    }

    @Override
    public void registerUserCallback(String username, UserCallback callbackObject) throws RemoteException, UserNotExistsException {
        username = WinsomeHelper.normalizeUsername(username);
        Lock rLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        boolean userExist = registeredUsers.containsKey(username);
        rLock.unlock();

        if(!userExist) {
            throw new UserNotExistsException();
        }

        synchronized (registeredCallbacks) {
            if(registeredCallbacks.containsKey(username)) {
                registeredCallbacks.replace(username, callbackObject);
            } else {
                registeredCallbacks.put(username, callbackObject);
            }
        }
    }

    @Override
    public void unregisterUserCallback(String username, UserCallback callbackObject) throws RemoteException, UserNotExistsException {
        username = WinsomeHelper.normalizeUsername(username);
        Lock rLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        boolean userExist = registeredUsers.containsKey(username);
        rLock.unlock();

        if(!userExist) {
            throw new UserNotExistsException();
        }

        synchronized (registeredCallbacks) {
            registeredCallbacks.remove(username);
        }
    }

    public boolean hasSession(String username) {
        synchronized (currentSessions) {
            return currentSessions.get(username) != null;
        }
    }

    public boolean removeSession(SelectionKey caller) {
        if(caller == null) throw new NullPointerException("Caller cannot be null");
        User current = (User) caller.attachment();
        if(current != null) {
            caller.attach(null);
            Lock rLock = WinsomeHelper.acquireReadLock(currentSessionsRW);
            boolean wasRemoved = currentSessions.remove(current.getUsername()) != null;
            rLock.unlock();
            caller.attach(null);
            return wasRemoved;
        }

        return false;
    }

    public NetResponseType makeSession(String username, String password, SelectionKey caller) {
        Lock sessionLock = WinsomeHelper.acquireWriteLock(currentSessionsRW);
        if(currentSessions.get(username) != null) {
            sessionLock.unlock();
            return UserAlreadyLoggedIn;
        }

        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        User user = registeredUsers.get(username);
        if(user == null) {
            WinsomeHelper.releaseAllLocks(sessionLock, userLock);
            return NetResponseType.UsernameNotExists;
        } else if(!password.equals(user.getPassword())) {
            WinsomeHelper.releaseAllLocks(sessionLock, userLock);
            return NetResponseType.WrongPassword;
        }

        currentSessions.put(username, caller);
        sessionLock.unlock();
        userLock.unlock();
        caller.attach(user);

        Lock blogLock = WinsomeHelper.acquireWriteLock(cachedBlogsRW);
        cachedBlogs.putIfAbsent(username, new LinkedList<>());
        blogLock.unlock();

        return NetResponseType.Success;
    }

    public void notifyFollowAdded(String from, String to) throws RemoteException, NullPointerException {
        synchronized (registeredCallbacks) {
            UserCallback cb = registeredCallbacks.get(to);
            if(cb != null) {
                cb.onFollowReceived(from);
            }
        }
    }

    public void notifyFollowRemoved(String from, String to) throws RemoteException, NullPointerException {
        synchronized (registeredCallbacks) {
            UserCallback cb = registeredCallbacks.get(to);
            if(cb != null) {
                cb.onFollowRemoved(from);
            }
        }
    }

    public List<Post> getFeedByUsername(String username, int page) {
        final int pageSize = 5;
        User user;

        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        user = registeredUsers.get(username);
        userLock.unlock();

        if(user == null) return null;
        int startFrom = page * pageSize;
        int endAt = startFrom + pageSize;
        List<Post> posts = new ArrayList<>();
        int postIndex = 0;

        Lock postLock = WinsomeHelper.acquireReadLock(postMapRW);
        if(startFrom > postMap.size())
            return posts;

        for(Map.Entry<Post, Post> entry : postMap.entrySet()) {
            if(postIndex++ < startFrom) continue;
            Post currentPost = entry.getValue();
            if(!currentPost.getUsername().equals(username) && user.hasUserFollowed(currentPost.getUsername())) {
                posts.add(currentPost.deepCopyAs());
            }

            if(postIndex == endAt) {
                break;
            }
        }
        postLock.unlock();

        return posts;
    }

    public List<Post> getBlogByUsername(String username, int page) {
        final int pageSize = 5;
        int pageStart = page * pageSize;
        int endPage = pageStart + pageSize;

        List<Post> result;
        Lock blogLock = WinsomeHelper.acquireReadLock(cachedBlogsRW);
        List<Post> curr = cachedBlogs.get(username);
        if(curr.size() < pageStart)
            return new ArrayList<>();

        int toIndex = Math.min(curr.size(), endPage);
        result = curr.subList(pageStart, toIndex).stream().map(BaseSocialEntity::<Post>deepCopyAs)
                    .collect(Collectors.toList());
        blogLock.unlock();
        return result;
    }

    public List<User> getSuggestedUsersByTags(Collection<String> tags, String skipUsername) {
        List<User> similarUsers = new ArrayList<>();

        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        for (User user : registeredUsers.values()) {
            if(user.getUsername().equals(skipUsername)) continue;
            if(user.hasSimilarTags(tags)) {
                try {
                    similarUsers.add((User) user.clone());
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
            }
        }
        userLock.unlock();
        return similarUsers;
    }

    public NetResponseType addPost(Post post) {
        int generatedId = maxPostId.get();
        Post inserted;

        Lock postLock = WinsomeHelper.acquireWriteLock(postMapRW);
        if(post.isRewin()) {
            Post originalPost = postMap.get(post.getOriginalPost());
            if(originalPost == null) {
                postLock.unlock();
                return OriginalPostNotExists;
            }

            while(originalPost.isRewin()) {
                originalPost = originalPost.getOriginalPost();
            }

            if(originalPost.getUsername().equals(post.getUsername())) {
                postLock.unlock();
                return UserSelfRewin;
            }

            post.setOriginalPost(originalPost);
        }

        do {
            post.setId(generatedId++);
        } while(postMap.containsKey(post));

        maxPostId.set(generatedId);
        inserted = post.deepCopyAs();
        postMap.put(inserted, inserted);
        postLock.unlock();

        Lock blogLock = WinsomeHelper.acquireWriteLock(cachedBlogsRW);
        cachedBlogs.get(post.getUsername())
                .add(0, inserted);
        blogLock.unlock();
        return Success;
    }

    public Post getPost(int id) {
        Lock postLock = WinsomeHelper.acquireReadLock(postMapRW);
        Post post = postMap.get(new Post(id));
        postLock.unlock();

        return post == null ? null : post.deepCopyAs();
    }

    public boolean removePost(int id) {
        List<Post> deletedPosts = new ArrayList<>();

        Lock postLock = WinsomeHelper.acquireWriteLock(postMapRW);
        Post post = postMap.get(new Post(id));
        if(post == null) {
            postLock.unlock();
            return false;
        }

        List<Integer> deletedCommentsId = new ArrayList<>();
        Lock commentLock = WinsomeHelper.acquireWriteLock(commentMapRW);
        if(!post.isRewin()) {
            postMap.values().removeIf(p -> {
                if(p.isRewin() && p.getOriginalPost().getId() == id) {
                    deletedPosts.add(p);
                    for(Comment comment : p.getComments()) {
                        deletedCommentsId.add(comment.getId());
                    }
                    return true;
                }
                return false;
            });
        }

        for(Comment comment : post.getComments()) {
            deletedCommentsId.add(comment.getId());
        }

        if(deletedCommentsId.size() > 0) {
            for (Integer idComment : deletedCommentsId) {
                commentMap.remove(idComment);
            }
        }
        commentLock.unlock();

        postMap.remove(post);
        deletedPosts.add(post);
        postLock.unlock();

        Lock blogLock = WinsomeHelper.acquireWriteLock(cachedBlogsRW);
        for(Post currentPost : deletedPosts) {
            cachedBlogs.get(currentPost.getUsername()).remove(currentPost);
        }
        blogLock.unlock();
        return true;
    }

    public boolean removePostIfOwner(int id, String from) throws NoAuthorizationException {
        Post post = getPost(id);
        if(post == null) {
            return false;
        }

        if(!post.getUsername().equals(from)) {
            throw new NoAuthorizationException(String.format("%s does not have permission to delete post %d", from, id));
        }

        return removePost(id);
    }

    public NetResponseType addVote(int entityId, VotableType type, VoteType vote, User user) {
        if(type == VotableType.Post) {
            Post post = getPost(entityId);
            if(post == null) {
                return NetResponseType.EntityNotExists;
            }

            if(post.getUsername().equals(user.getUsername())) {
                return NetResponseType.UserSelfVote;
            }

            if(!user.hasUserFollowed(post.getUsername())) {
                return NetResponseType.PostNotInFeed;
            }

            if(post.getVote(user.getUsername()) != null) {
                return NetResponseType.UserAlreadyVoted;
            }

            post.addVote(user.getUsername(), vote);
            return NetResponseType.Success;
        } else if(type == VotableType.Comment) {
            Comment comment = getComment(entityId);
            if(comment == null) {
                return NetResponseType.EntityNotExists;
            }

            if(comment.getOwner().equals(user.getUsername())) {
                return NetResponseType.UserSelfVote;
            }

            if(comment.getVote(user.getUsername()) != null) {
                return NetResponseType.UserAlreadyVoted;
            }

            comment.addVote(user.getUsername(), vote);
            return NetResponseType.Success;
        }

        return NetResponseType.InvalidParameters;
    }

    public NetResponseType addComment(Comment comment, User user) {
        Post targetPost = getPost(comment.getPostId());
        if(targetPost == null) {
            return EntityNotExists;
        }

        if(targetPost.getUsername().equals(user.getUsername())) {
            return UserSelfComment;
        }

        if(!user.hasUserFollowed(targetPost.getUsername())) {
            return PostNotInFeed;
        }

        Lock commentLock = WinsomeHelper.acquireWriteLock(commentMapRW);
        int generatedId = maxCommentId.get();
        do {
            generatedId++;
        } while(commentMap.containsKey(generatedId));

        comment.setId(generatedId);
        Comment inserted = comment.deepCopyAs();
        commentMap.put(comment.getId(), inserted);
        maxCommentId.set(generatedId);
        commentLock.unlock();

        targetPost.addComment(inserted);
        return Success;
    }
    
    public Comment getComment(int id) {
        Lock commentLock = WinsomeHelper.acquireReadLock(commentMapRW);
        Comment comment = commentMap.get(id);
        commentLock.unlock();

        return comment;
    }

    public User getUserByUsername(String username) {
        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        User user = registeredUsers.get(username);
        userLock.unlock();

        return user;
    }

    public Map<String, User> getUsersResource() {
        WinsomeHelper.acquireReadLock(registeredUsersRW);
        return registeredUsers;
    }

    public void unlockUsers() {
        registeredUsersRW.writeLock().unlock();
    }

    public Collection<Post> getPostsResource() {
        WinsomeHelper.acquireReadLock(postMapRW);
        return postMap.values();
    }

    public void unlockPosts() {
        postMapRW.writeLock().unlock();
    }
}
