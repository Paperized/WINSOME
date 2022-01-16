package it.winsome.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import it.winsome.common.WinsomeHelper;
import it.winsome.common.entity.Comment;
import it.winsome.common.entity.Post;
import it.winsome.common.entity.User;
import it.winsome.common.entity.Vote;
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

import static it.winsome.common.network.enums.NetResponseType.*;

public class UserServiceImpl extends UnicastRemoteObject implements UserService {
    private final Map<String, User> registeredUsers;
    private final Map<String, UserCallback> registeredCallbacks;
    private final Map<String, SelectionKey> currentSessions;
    private final Map<String, LinkedList<Post>> cachedBlogs;

    private int maxPostId;
    private final Map<Post, Post> postMap;
    private int maxCommentId;
    private final Map<Integer, Comment> commentMap;

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

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

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
            for(Post post : posts) {
                postMap.put(post, post);
                maxPostId = Math.max(maxPostId, post.getId());
            }
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
            for(Comment comment : comments) {
                temp.setId(comment.getPostId());
                Post referredPost = postMap.get(temp);
                if(referredPost == null) {
                    WinsomeHelper.printfDebug("WARNING Load from disk! Comment with id %d set with post id %d but it does not exists!");
                    continue;
                }
                referredPost.addComment(comment);
                commentMap.put(comment.getId(), comment);
                maxCommentId = Math.max(maxCommentId, comment.getId());
            }
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

        WinsomeHelper.printfDebug("Max id post: %d", maxPostId);
        saveToDisk();
    }

    @Override
    public User registerUser(String username, String password, String[] tags) throws RemoteException, UserAlreadyExistsException, NoTagsFoundException {
        username = WinsomeHelper.normalizeUsername(username);
        User user = new User(username, password, tags);
        if(!user.hasAnyTag()) {
            throw new NoTagsFoundException();
        }

        synchronized (registeredUsers) {
            User oldUser = registeredUsers.putIfAbsent(username, user);
            if(oldUser != null) {
                throw new UserAlreadyExistsException();
            }
        }

        return user;
    }

    @Override
    public void registerUserCallback(String username, UserCallback callbackObject) throws RemoteException, UserNotExistsException {
        username = WinsomeHelper.normalizeUsername(username);
        synchronized (registeredUsers) {
            if(!registeredUsers.containsKey(username)) {
                throw new UserNotExistsException();
            }
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
        synchronized (registeredUsers) {
            if(!registeredUsers.containsKey(username)) {
                throw new UserNotExistsException();
            }
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
            synchronized (currentSessions) {
                caller.attach(null);
                return currentSessions.remove(current.getUsername()) != null;
            }
        }

        return false;
    }

    public NetResponseType makeSession(String username, String password, SelectionKey caller) {
        synchronized (registeredUsers) {
            synchronized (currentSessions) {
                if(currentSessions.get(username) != null) {
                    return UserAlreadyLoggedIn;
                }

                User user = registeredUsers.get(username);
                if(user == null) {
                    return NetResponseType.UsernameNotExists;
                } else if(!password.equals(user.getPassword())) {
                    return NetResponseType.WrongPassword;
                }

                currentSessions.put(username, caller);
                caller.attach(user);
            }
        }

        synchronized (cachedBlogs) {
            cachedBlogs.putIfAbsent(username, new LinkedList<>());
        }
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
        synchronized (registeredUsers) {
            user = registeredUsers.get(username);
        }

        if(user == null) return null;
        int startFrom = page * pageSize;
        int endAt = startFrom + pageSize;
        List<Post> posts = new ArrayList<>();
        int postIndex = 0;
        synchronized (postMap) {
            if(startFrom > postMap.size())
                return posts;

            for(Map.Entry<Post, Post> entry : postMap.entrySet()) {
                if(postIndex++ < startFrom) continue;
                Post currentPost = entry.getValue();
                if(!currentPost.getUsername().equals(username) && user.hasUserFollowed(currentPost.getUsername())) {
                    posts.add(currentPost);
                }

                if(postIndex == endAt) {
                    break;
                }
            }
        }

        return posts;
    }

    public List<Post> getBlogByUsername(String username, int page) {
        final int pageSize = 5;
        int pageStart = page * pageSize;
        int endPage = pageStart + pageSize;
        synchronized (cachedBlogs) {
            List<Post> curr = cachedBlogs.get(username);
            if(curr.size() < pageStart)
                return new ArrayList<>();

            int toIndex = Math.min(curr.size(), endPage);
            return curr.subList(pageStart, toIndex);
        }
    }

    public List<User> getSuggestedUsersByTags(Collection<String> tags, String skipUsername) {
        List<User> similarUsers = new ArrayList<>();
        synchronized (registeredUsers) {
            for (User user : registeredUsers.values()) {
                if(user.getUsername().equals(skipUsername)) continue;
                if(user.hasSimilarTags(tags)) {
                    similarUsers.add(user);
                }
            }
        }

        return similarUsers;
    }

    public NetResponseType addPost(Post post) {
        int generatedId = maxPostId;
        synchronized (postMap) {
            if(post.isRewin()) {
                Post originalPost = postMap.get(post.getOriginalPost());
                if(originalPost == null) {
                    return OriginalPostNotExists;
                }

                while(originalPost.isRewin()) {
                    originalPost = originalPost.getOriginalPost();
                }

                if(originalPost.getUsername().equals(post.getUsername()))
                    return UserSelfRewin;

                post.setOriginalPost(originalPost);
            }

            do {
                post.setId(generatedId++);
            } while(postMap.containsKey(post));

            maxPostId = generatedId;
            postMap.put(post, post);
        }

        synchronized (cachedBlogs) {
            cachedBlogs.get(post.getUsername())
                    .add(0, post);
            return Success;
        }
    }

    public Post getPost(int id) {
        synchronized (postMap) {
            return postMap.get(new Post(id));
        }
    }

    public boolean removePost(int id) {
        List<Post> deletedPosts = new ArrayList<>();

        synchronized (postMap) {
            Post post = postMap.get(new Post(id));
            if(post == null)
                return false;

            List<Integer> deletedCommentsId = new ArrayList<>();
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
                synchronized (commentMap) {
                    for (Integer idComment : deletedCommentsId) {
                        commentMap.remove(idComment);
                    }
                }
            }

            postMap.remove(post);
            deletedPosts.add(post);
        }

        synchronized (cachedBlogs) {
            for(Post post : deletedPosts) {
                cachedBlogs.get(post.getUsername()).remove(post);
            }
            return true;
        }
    }

    public boolean removePostIfOwner(int id, String from) throws NoAuthorizationException {
        synchronized (postMap) {
            Post post = postMap.get(new Post(id));
            if(post == null)
                return false;
            if(!post.getUsername().equals(from))
                throw new NoAuthorizationException(String.format("%s does not have permission to delete post %d", from, id));

            List<Integer> deletedCommentsId = new ArrayList<>();
            if(!post.isRewin()) {
                postMap.values().removeIf(p -> {
                    if(p.isRewin() && p.getOriginalPost().getId() == id) {
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
                synchronized (commentMap) {
                    for (Integer idComment : deletedCommentsId) {
                        commentMap.remove(idComment);
                    }
                }
            }

            postMap.remove(post);
            return true;
        }
    }

    public NetResponseType addVote(int entityId, VotableType type, VoteType vote, User user) {
        if(type == VotableType.Post) {
            synchronized (postMap) {
                Post post = postMap.get(new Post(entityId));
                if(post == null) {
                    return NetResponseType.EntityNotExists;
                }

                if(post.getUsername().equals(user.getUsername())) {
                    return NetResponseType.UserSelfVote;
                }

                if(!user.hasUserFollowed(post.getUsername())) {
                    return NetResponseType.PostNotInFeed;
                }

                Vote prevVote = post.getVote(user.getUsername());
                if(prevVote != null) {
                    return NetResponseType.UserAlreadyVoted;
                }

                post.addVote(user.getUsername(), vote);
                return NetResponseType.Success;
            }
        } else if(type == VotableType.Comment) {
            synchronized (commentMap) {
                Comment comment = commentMap.get(entityId);
                if(comment == null) {
                    return NetResponseType.EntityNotExists;
                }

                if(comment.getOwner().equals(user.getUsername())) {
                    return NetResponseType.UserSelfVote;
                }

                Vote prevVote = comment.getVote(user.getUsername());
                if(prevVote != null) {
                    return NetResponseType.UserAlreadyVoted;
                }

                comment.addVote(user.getUsername(), vote);
                return NetResponseType.Success;
            }
        }

        return NetResponseType.InvalidParameters;
    }

    public boolean hasPost(int id) {
        synchronized (postMap) {
            return postMap.containsKey(new Post(id));
        }
    }

    public NetResponseType addComment(Comment comment, User user) {
        synchronized (postMap) {
            Post targetPost = postMap.get(new Post(comment.getPostId()));
            if(targetPost == null) {
                return EntityNotExists;
            }

            if(targetPost.getUsername().equals(user.getUsername())) {
                return UserSelfComment;
            }

            if(!user.hasUserFollowed(targetPost.getUsername())) {
                return PostNotInFeed;
            }

            targetPost.addComment(comment);
            synchronized (commentMap) {
                int generatedId = maxCommentId;
                do {
                    generatedId++;
                } while(commentMap.containsKey(generatedId));

                comment.setId(generatedId);
                commentMap.put(comment.getId(), comment);
                maxCommentId = generatedId;
                return Success;
            }
        }
    }

    public Comment getComment(int id) {
        synchronized (commentMap) {
            return commentMap.get(id);
        }
    }

    public boolean hasComment(int id) {
        synchronized (commentMap) {
            return commentMap.containsKey(id);
        }
    }

    public User getUserByUsername(String username) {
        synchronized (registeredUsers) {
            return registeredUsers.get(username);
        }
    }

    public boolean doUserExists(String username) {
        synchronized (registeredUsers) {
            return registeredUsers.containsKey(username);
        }
    }
}
