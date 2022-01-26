package it.winsome.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import it.winsome.common.SynchronizedObject;
import it.winsome.common.WinsomeHelper;
import it.winsome.common.entity.*;
import it.winsome.common.entity.enums.CurrencyType;
import it.winsome.common.entity.enums.VotableType;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.exception.*;
import it.winsome.common.network.enums.NetResponseType;
import it.winsome.common.service.interfaces.UserCallbackClient;
import it.winsome.server.session.ConnectionSession;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static it.winsome.common.network.enums.NetResponseType.*;

/**
 * Main logic of the social network, it offers many functionality thread safe to query or change
 * other entities.
 * It is pure social network logic and does not involve any client socket except from the makeSession which needs a SelectionKey
 */
public class ServerLogic {
    private final Map<String, User> registeredUsers;
    private final ReadWriteLock registeredUsersRW;
    private final Map<String, UserCallbackClient> registeredCallbacks;
    private final Map<String, SelectionKey> currentSessions;
    private final ReadWriteLock currentSessionsRW;
    private final Map<String, LinkedList<Post>> cachedBlogs;
    private final ReadWriteLock cachedBlogsRW;

    private final AtomicInteger maxPostId;
    private final Map<Post, Post> postMap;
    private final List<Post> postList;
    private final ReadWriteLock postMapRW;
    private final AtomicInteger maxCommentId;
    private final Map<Integer, Comment> commentMap;
    private final ReadWriteLock commentMapRW;

    private final String dataFolder;
    private final URL btcConverterURL;
    private boolean initialized;

    public ServerLogic(String dataFolder) throws MalformedURLException {
        super();
        this.dataFolder = dataFolder;
        registeredCallbacks = new HashMap<>();
        registeredUsers = new HashMap<>();
        postMap = new LinkedHashMap<>();
        postList = new ArrayList<>();
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

        btcConverterURL = new URL("https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new");

        try {
            if(loadFromDisk()) {
                WinsomeHelper.printlnDebug("Data loaded succesfully!");
            } else {
                WinsomeHelper.printlnDebug("Error during data loading, partial data might be loaded!");
            }
        } catch (DataAlreadyLoadedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save all entities in different files
     * @return true if all entities have been saved, false if saved partially
     */
    public synchronized boolean saveToDisk() {
        boolean allCompleted = true;
        Gson gsonPost = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        Lock rLock = WinsomeHelper.acquireReadLock(postMapRW);
        WinsomeHelper.prepareReadCollection(postMap.values());
        String jsonPost = gsonPost.toJson(postMap.values());
        WinsomeHelper.releaseReadCollection(postMap.values());
        rLock.unlock();

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

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        rLock = WinsomeHelper.acquireReadLock(commentMapRW);
        WinsomeHelper.prepareReadCollection(commentMap.values());
        String jsonComment = gson.toJson(commentMap.values());
        WinsomeHelper.releaseReadCollection(commentMap.values());
        rLock.unlock();

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

        rLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        WinsomeHelper.prepareReadCollection(registeredUsers.values());
        String jsonUser = gson.toJson(registeredUsers.values());
        WinsomeHelper.releaseReadCollection(registeredUsers.values());
        rLock.unlock();

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

        return allCompleted;
    }

    /**
     * Load all entities from disk on startup
     * @return true if all data have been loaded, false if partially
     * @throws DataAlreadyLoadedException if the load is called more then one time
     */
    public synchronized boolean loadFromDisk() throws DataAlreadyLoadedException {
        if(initialized) throw new DataAlreadyLoadedException();
        initialized = true;
        boolean allCompleted = true;
        Gson gsonPost = new GsonBuilder().create();

        try {
            String jsonPost = new String(Files.readAllBytes(Paths.get(dataFolder + "posts.json")), StandardCharsets.UTF_8);
            List<Post> posts = gsonPost.fromJson(jsonPost, new TypeToken<List<Post>>(){}.getType());
            posts.sort((o1, o2) -> o2.getCreationDate().compareTo(o1.getCreationDate()));
            int maxPostIdTemp = 0;
            for(Post post : posts) {
                post.setTotalComments(0);
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

        //enable synchronization and create blogs
        registeredUsers.forEach((k, v) -> {
            v.enableSynchronization(true);
            cachedBlogs.put(k, new LinkedList<>());
        });

        postMap.forEach((k, v) -> {
            cachedBlogs.get(v.getUsername()).add(v);
            postList.add(v);
            v.enableSynchronization(true);
        });
        commentMap.forEach((k, v) -> v.enableSynchronization(true));

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
        addVote(p.getId(), VotableType.Post, VoteType.UP, "piero");
        Comment c = new Comment(-1, "piero", "Bel post niente da dire");
        c.setPostId(p.getId());
        c.addVote(new Vote("ghfjy787", VoteType.UP));
        addComment(c, registeredUsers.get("piero"));
        c = new Comment(-1, "ghfjy787", "Non saprei meh..");
        c.setPostId(p.getId());
        c.addVote(new Vote("piero", VoteType.UP));
        addComment(c, registeredUsers.get("ghfjy787"));
        c = new Comment(-1, "piero", "Bel post niente da dire");
        c.addVote(new Vote("ghfjy787", VoteType.UP));
        addComment(c, registeredUsers.get("piero"));
        Post rewin = new Post(-1, "piero", "GIANMARCO MA COSA DICI PORCODDIOOOOO!", "VA IN MONA CO YOUTUBE!");
        rewin.setOriginalPost(new Post(p.getId()));
        addPost(rewin);
        addPost(new Post(-1, "piero", "Ecco perchè le monete virtuali sono OP AF!", "Allora praticamente I BTC sono la vostra salvezza\n, dovete comprarne all'infinito e basta.. XD"));
        p = new Post(-1, "piero", "BITCOIN SU FORTNITE!!!", "Pazzesco io boh, sicuramente comprero il panino di Trevis COTT!");
        addPost(p);
        c = new Comment(-1, "piero", "Bel post niente da dire");
        c.setPostId(p.getId());
        c.addVote(new Vote("ghfjy787", VoteType.UP));
        addComment(c, registeredUsers.get("piero"));
        c = new Comment(-1, "piero", "Bel post niente da dire");
        c.addVote(new Vote("ghfjy787", VoteType.UP));
        addComment(c, registeredUsers.get("piero"));
        c = new Comment(-1, "piero", "Bel post niente da dire");
        c.addVote(new Vote("ghfjy787", VoteType.UP));
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

    /**
     * Create a new user
     * @param username username
     * @param password sha516 password
     * @param tags tags
     * @return a copy of the new user
     * @throws UserAlreadyExistsException if the username already exists
     * @throws NoTagsFoundException if no tags are found
     */
    public User registerUser(String username, String password, String[] tags) throws UserAlreadyExistsException, NoTagsFoundException {
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

    /**
     * Register an interface for asynchronous callback to client
     * @param username username
     * @param callbackObject callback interface
     * @throws UserNotExistsException if the user does not exists
     */
    public void registerUserCallback(String username, UserCallbackClient callbackObject) throws UserNotExistsException {
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

    /**
     * Unregister a callback interface
     * @param username username
     * @param callbackObject callback
     * @throws UserNotExistsException exception
     */
    public void unregisterUserCallback(String username, UserCallbackClient callbackObject) throws UserNotExistsException {
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

    /**
     * Remove a selection key from the session
     * @param caller client
     * @return true if removed
     */
    public boolean removeSession(SelectionKey caller) {
        if(caller == null) throw new NullPointerException("Caller cannot be null");
        User current = ((ConnectionSession) caller.attachment()).getUserLogged();
        if(current != null) {
            ((ConnectionSession)caller.attachment()).setUserLogged(null);
            Lock rLock = WinsomeHelper.acquireReadLock(currentSessionsRW);
            boolean wasRemoved = currentSessions.remove(current.getUsername()) != null;
            rLock.unlock();
            return wasRemoved;
        }

        return false;
    }

    /**
     * Create a new session between this username and this caller
     * @param username username
     * @param password password
     * @param caller caller
     * @return result response
     */
    public NetResponseType makeSession(String username, String password, SelectionKey caller) {
        Lock sessionLock = WinsomeHelper.acquireWriteLock(currentSessionsRW);
        if(currentSessions.get(username) != null) {
            sessionLock.unlock();
            return UserAlreadyLoggedIn;
        }

        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        User user = registeredUsers.get(username);
        User userCopy;
        if(user != null) {
            user.prepareRead();
            if(!password.equals(user.getPassword())) {
                user.releaseRead();
                WinsomeHelper.releaseAllLocks(sessionLock, userLock);
                return NetResponseType.WrongPassword;
            }
            userCopy = user.deepCopy();
            user.releaseRead();
        } else {
            WinsomeHelper.releaseAllLocks(sessionLock, userLock);
            return NetResponseType.UsernameNotExists;
        }

        currentSessions.put(username, caller);
        userLock.unlock();
        sessionLock.unlock();
        ((ConnectionSession)caller.attachment()).setUserLogged(userCopy);

        Lock blogLock = WinsomeHelper.acquireWriteLock(cachedBlogsRW);
        cachedBlogs.putIfAbsent(username, new LinkedList<>());
        blogLock.unlock();

        return NetResponseType.Success;
    }

    /**
     * Notify the receiver
     * @param from user following
     * @param to user receiving the follow
     * @throws RemoteException remote exception
     * @throws NullPointerException null exception
     */
    public void notifyFollowAdded(String from, String to) throws RemoteException, NullPointerException {
        synchronized (registeredCallbacks) {
            UserCallbackClient cb = registeredCallbacks.get(to);
            if(cb != null) {
                cb.onFollowReceived(from);
            }
        }
    }

    /**
     * Notify the receiver
     * @param from user removing the follow
     * @param to user being removed
     * @throws RemoteException remote exception
     * @throws NullPointerException exception
     */
    public void notifyFollowRemoved(String from, String to) throws RemoteException, NullPointerException {
        synchronized (registeredCallbacks) {
            UserCallbackClient cb = registeredCallbacks.get(to);
            if(cb != null) {
                cb.onFollowRemoved(from);
            }
        }
    }

    /**
     * Add follow from a user to another
     * @param from user following
     * @param to user followed
     * @return result response
     */
    public NetResponseType addFollow(String from, String to) {
        if(from.equalsIgnoreCase(to)) {
            return UserSelfFollow;
        } else {
            User followedUser = getRealUserByUsername(to);
            if(followedUser == null) {
                return UsernameNotExists;
            } else {
                User fromUser = getRealUserByUsername(from);
                SynchronizedObject.prepareInWriteMode(fromUser);
                fromUser.addUserFollowed(to);
                fromUser.releaseWrite();

                SynchronizedObject.prepareInWriteMode(followedUser);
                if(followedUser.addUserFollowing(from)) {
                    followedUser.releaseWrite();

                    try {
                        notifyFollowAdded(from, to);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                followedUser.releaseWrite();
                return Success;
            }
        }
    }

    /**
     * Remove follow
     * @param from user removing
     * @param to user removed
     * @return result response
     */
    public NetResponseType removeFollow(String from, String to) {
        if(from.equalsIgnoreCase(to)) {
            return UserSelfFollow;
        } else {
            User followedUser = getRealUserByUsername(to);
            if(followedUser == null) {
                return UsernameNotExists;
            } else {
                User fromUser = getRealUserByUsername(from);
                SynchronizedObject.prepareInWriteMode(fromUser);
                if(!fromUser.removeUserFollowed(to)) {
                    fromUser.releaseWrite();
                    return UserNotFollowed;
                }
                else {
                    fromUser.releaseWrite();
                    SynchronizedObject.prepareInWriteMode(followedUser);
                    if(followedUser.removeUserFollowing(from)) {
                        followedUser.releaseWrite();
                        try {
                            notifyFollowRemoved(from, to);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    followedUser.releaseWrite();
                    return Success;
                }
            }
        }
    }

    /**
     * Get the feed (paginated) of a certain user
     * @param username username
     * @param page page
     * @return a list copy
     */
    public List<Post> getFeedByUsername(String username, int page) {
        final int pageSize = 5;
        User user;

        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        user = registeredUsers.get(username);
        userLock.unlock();

        if(user == null) return null;
        List<Post> posts = new ArrayList<>();

        Lock postLock = WinsomeHelper.acquireReadLock(postMapRW);
        if(postList.size() == 0) {
            postLock.unlock();
            return posts;
        }

        int postToSkip = page * pageSize;
        int postSkipped = 0;
        int postAdded = 0;

        user.prepareRead();
        for(int i = postList.size() - 1; i >= 0; i--) {
            Post post = postList.get(i);
            post.prepareRead();
            if(!post.getUsername().equals(username) && user.hasUserFollowed(post.getUsername())) {
                if(postSkipped < postToSkip) {
                    postSkipped++;
                } else {
                    postAdded++;
                    posts.add(post.deepCopyAs());
                    if(postAdded == pageSize) {
                        post.releaseRead();
                        break;
                    }
                }
            }
            post.releaseRead();
        }
        user.releaseRead();
        postLock.unlock();

        return posts;
    }

    /**
     * Get the blog posts (paginated) of a certain username
     * @param username username
     * @param page page
     * @return result response
     */
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
        result = WinsomeHelper.deepCopySynchronizedList(curr.subList(pageStart, toIndex).stream());
        blogLock.unlock();
        return result;
    }

    /**
     * Find suggested users by common tags
     * @param tags common tags
     * @param skipUsername user to be skipped (usually the caller)
     * @return a list of similar users
     */
    public List<User> getSuggestedUsersByTags(Collection<String> tags, String skipUsername) {
        List<User> similarUsers = new ArrayList<>();

        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        for (User user : registeredUsers.values()) {
            user.prepareRead();
            if(user.getUsername().equals(skipUsername)) {
                user.releaseRead();
                continue;
            }

            if(user.hasSimilarTags(tags)) {
                similarUsers.add(user.deepCopy());
            }
            user.releaseRead();
        }
        userLock.unlock();
        return similarUsers;
    }

    /**
     * Add a post to the social network, it can also be a REWIN by setting the OriginalPost field
     * @param post Post to be added
     * @return result response
     */
    public NetResponseType addPost(Post post) {
        Post inserted;
        Post realOriginalPost = null;

        Lock postLock = WinsomeHelper.acquireWriteLock(postMapRW);
        int generatedId = maxPostId.get();
        if(post.isRewin()) {
            realOriginalPost = postMap.get(post.getOriginalPost());
            if(realOriginalPost == null) {
                postLock.unlock();
                return OriginalPostNotExists;
            }

            realOriginalPost.prepareRead();
            while(realOriginalPost.isRewin()) {
                realOriginalPost = realOriginalPost.getOriginalPost();
            }

            if(realOriginalPost.getUsername().equals(post.getUsername())) {
                realOriginalPost.releaseRead();
                postLock.unlock();
                return UserSelfRewin;
            }

            realOriginalPost.releaseRead();
        }

        do {
            post.setId(generatedId++);
        } while(postMap.containsKey(post));

        maxPostId.set(generatedId);
        inserted = post.deepCopyAs();
        inserted.setOriginalPost(realOriginalPost);
        postMap.put(inserted, inserted);
        postList.add(inserted);
        inserted.enableSynchronization(true);
        postLock.unlock();

        Lock blogLock = WinsomeHelper.acquireWriteLock(cachedBlogsRW);
        cachedBlogs.get(post.getUsername())
                .add(0, inserted);
        blogLock.unlock();
        return Success;
    }

    /**
     * Get the post copy
     * @param id id post
     * @return post copy
     */
    public Post getPost(int id) {
        Lock postLock = WinsomeHelper.acquireReadLock(postMapRW);
        Post post = postMap.get(new Post(id));
        postLock.unlock();

        if(post != null) {
            post.prepareRead();
            Post temp = post;
            post = post.deepCopyAs();
            temp.releaseRead();
        }

        return post;
    }

    /**
     * Get the real post reference
     * @param id id post
     * @return post
     */
    private Post getRealPost(int id) {
        Lock postLock = WinsomeHelper.acquireReadLock(postMapRW);
        Post post = postMap.get(new Post(id));
        postLock.unlock();

        return post;
    }

    /**
     * Remove a post by id, then remove all rewin and comments referred to that post
     * @param id id post
     * @return true if removed
     */
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
        post.prepareRead();
        if(!post.isRewin()) {
            postMap.values().removeIf(p -> {
                p.prepareRead();
                if(p.isRewin()) {
                    Post originalP = p.getOriginalPost();
                    originalP.prepareRead();
                    if(originalP.getId() == id) {
                        deletedPosts.add(p);
                        for(Comment comment : p.getComments()) {
                            comment.prepareRead();
                            deletedCommentsId.add(comment.getId());
                            comment.releaseRead();
                        }

                        originalP.releaseRead();
                        p.releaseRead();
                        return true;
                    }
                    originalP.releaseRead();
                }
                p.releaseRead();
                return false;
            });
        }

        for(Comment comment : post.getComments()) {
            comment.prepareRead();
            deletedCommentsId.add(comment.getId());
            comment.releaseRead();
        }
        post.releaseRead();

        if(deletedCommentsId.size() > 0) {
            for (Integer idComment : deletedCommentsId) {
                commentMap.remove(idComment);
            }
        }
        commentLock.unlock();

        deletedPosts.add(post);
        for(Post currentPost : deletedPosts) {
            postMap.remove(currentPost);
            postList.remove(currentPost);
        }
        postLock.unlock();

        Lock blogLock = WinsomeHelper.acquireWriteLock(cachedBlogsRW);
        for(Post currentPost : deletedPosts) {
            currentPost.prepareRead();
            cachedBlogs.get(currentPost.getUsername()).remove(currentPost);
            currentPost.releaseRead();
        }
        blogLock.unlock();
        return true;
    }

    /**
     * Remove the post only if the caller is the user
     * @param id post id
     * @param from caller
     * @return true if removed
     * @throws NoAuthorizationException if not authorized
     */
    public boolean removePostIfOwner(int id, String from) throws NoAuthorizationException {
        Post post = getRealPost(id);
        if(post == null) {
            return false;
        }

        post.prepareRead();
        if(!post.getUsername().equals(from)) {
            post.releaseRead();
            throw new NoAuthorizationException(String.format("%s does not have permission to delete post %d", from, id));
        }

        post.releaseRead();
        return removePost(id);
    }

    /**
     * Add a vote to an entity
     * @param entityId entity id
     * @param type entity type
     * @param vote vote type
     * @param username caller
     * @return result response
     */
    public NetResponseType addVote(int entityId, VotableType type, VoteType vote, String username) {
        if(type == VotableType.Post) {
            Lock rLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
            User user = registeredUsers.get(username);
            rLock.unlock();

            if(user == null) {
                return ClientNotLoggedIn;
            }

            Post post = getRealPost(entityId);
            if(post == null) {
                return NetResponseType.EntityNotExists;
            }

            SynchronizedObject.prepareInWriteMode(post);
            user.prepareRead();
            if(post.getUsername().equals(user.getUsername())) {
                post.releaseWrite();
                user.releaseRead();
                return NetResponseType.UserSelfVote;
            }

            if(!user.hasUserFollowed(post.getUsername())) {
                post.releaseWrite();
                user.releaseRead();
                return NetResponseType.PostNotInFeed;
            }

            if(post.getVote(user.getUsername()) != null) {
                post.releaseWrite();
                user.releaseRead();
                return NetResponseType.UserAlreadyVoted;
            }

            Vote voteEntity = new Vote(user.getUsername(), vote);
            post.addVote(voteEntity);
            voteEntity.enableSynchronization(true);
            post.releaseWrite();
            user.releaseRead();

            return NetResponseType.Success;
        } else if(type == VotableType.Comment) {
            Lock rLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
            User user = registeredUsers.get(username);
            rLock.unlock();

            if(user == null) {
                return ClientNotLoggedIn;
            }

            Comment comment = getRealComment(entityId);
            if(comment == null) {
                return NetResponseType.EntityNotExists;
            }

            SynchronizedObject.prepareInWriteMode(comment);
            user.prepareRead();
            if(comment.getOwner().equals(user.getUsername())) {
                comment.releaseWrite();
                user.releaseRead();
                return NetResponseType.UserSelfVote;
            }

            if(comment.getVote(user.getUsername()) != null) {
                comment.releaseWrite();
                user.releaseRead();
                return NetResponseType.UserAlreadyVoted;
            }

            Vote voteEntity = new Vote(user.getUsername(), vote);
            comment.addVote(voteEntity);
            comment.releaseWrite();
            user.releaseRead();
            voteEntity.enableSynchronization(true);
            return NetResponseType.Success;
        }

        return NetResponseType.InvalidParameters;
    }

    /**
     * Add a comment to a post
     * @param comment comment to be added
     * @param user caller
     * @return result response
     */
    public NetResponseType addComment(Comment comment, User user) {
        Post targetPost = getRealPost(comment.getPostId());
        if(targetPost == null) {
            return EntityNotExists;
        }

        SynchronizedObject.prepareInWriteMode(targetPost);
        user.prepareRead();
        if(targetPost.getUsername().equals(user.getUsername())) {
            user.releaseRead();
            targetPost.releaseWrite();
            return UserSelfComment;
        }

        if(!user.hasUserFollowed(targetPost.getUsername())) {
            user.releaseRead();
            targetPost.releaseWrite();
            return PostNotInFeed;
        }
        user.releaseRead();

        Lock commentLock = WinsomeHelper.acquireWriteLock(commentMapRW);
        int generatedId = maxCommentId.get();
        do {
            generatedId++;
        } while(commentMap.containsKey(generatedId));

        comment.setId(generatedId);
        Comment inserted = comment.deepCopyAs();
        commentMap.put(generatedId, inserted);
        maxCommentId.set(generatedId);

        targetPost.addComment(inserted);
        inserted.enableSynchronization(true);

        commentLock.unlock();
        targetPost.releaseWrite();
        return Success;
    }

    //unused
    public Comment getComment(int id) {
        Lock commentLock = WinsomeHelper.acquireReadLock(commentMapRW);
        Comment comment = commentMap.get(id);
        commentLock.unlock();

        if(comment != null) {
            comment.releaseRead();
            Comment temp = comment;
            comment = comment.deepCopyAs();
            temp.releaseRead();
        }

        return comment;
    }

    /**
     * Get the real reference to a comment by id
     * @param id comment id
     * @return comment
     */
    private Comment getRealComment(int id) {
        Lock commentLock = WinsomeHelper.acquireReadLock(commentMapRW);
        Comment comment = commentMap.get(id);
        commentLock.unlock();

        return comment;
    }

    /**
     * Get a wallet copy of a user by currency
     * @param username username
     * @param currency currency
     * @return the wallet copy
     * @throws IOException if the btc URL call was unsuccessful
     */
    public Wallet getWallet(String username, CurrencyType currency) throws IOException {
        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        if(!registeredUsers.containsKey(username)) {
            userLock.unlock();
            return null;
        }

        User user = registeredUsers.get(username);
        user.prepareRead();
        Wallet wallet = user.getWallet();
        user.releaseRead();
        userLock.unlock();

        if(currency == CurrencyType.Bitcoin) {
            URLConnection urlConnection = btcConverterURL.openConnection();
            urlConnection.connect();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream()));
            String multiplier = reader.readLine();
            reader.close();
            double multiplierDouble = Double.parseDouble(multiplier.trim());
            SynchronizedObject.prepareInWriteMode(wallet);
            wallet.setRateInBTC(multiplierDouble);
            wallet.downgradeToRead();
        }

        wallet.prepareRead();
        Wallet copy = wallet.deepCopyByCurrency(currency);
        wallet.releaseRead();
        return copy;
    }

    /**
     * Get the real reference of a user by username
     * @param username username
     * @return the user
     */
    public User getRealUserByUsername(String username) {
        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        User user = registeredUsers.get(username);
        userLock.unlock();

        return user;
    }

    /**
     * Check if the user exists
     * @param username username
     * @return true if exists
     */
    public boolean doUserExists(String username) {
        Lock userLock = WinsomeHelper.acquireReadLock(registeredUsersRW);
        boolean res = registeredUsers.containsKey(username);
        userLock.unlock();

        return res;
    }

    /**
     * Require the users resources, then acquire a read lock on it (to be released later with unlockUsers)
     * @return the user resource
     */
    public Map<String, User> getUsersResource() {
        WinsomeHelper.acquireReadLock(registeredUsersRW);
        return registeredUsers;
    }

    /**
     * Unlocks the users lock
     */
    public void unlockUsers() {
        registeredUsersRW.readLock().unlock();
    }

    /**
     * Require the post resources, then acquire a read lock on it (to be released later with unlockPosts)
     * @return the post resource
     */
    public Collection<Post> getPostsResource() {
        WinsomeHelper.acquireReadLock(postMapRW);
        return postMap.values();
    }

    /**
     * Unlocks the posts lock
     */
    public void unlockPosts() {
        postMapRW.readLock().unlock();
    }
}
