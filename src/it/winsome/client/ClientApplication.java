package it.winsome.client;

import it.winsome.client.config.ClientConfiguration;
import it.winsome.common.dto.*;
import it.winsome.common.entity.Comment;
import it.winsome.common.entity.Post;
import it.winsome.common.entity.User;
import it.winsome.common.entity.enums.CurrencyType;
import it.winsome.common.entity.enums.VotableType;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.exception.*;
import it.winsome.common.network.NetMessage;
import it.winsome.common.network.enums.NetMessageType;
import it.winsome.common.network.enums.NetResponseType;
import it.winsome.common.service.interfaces.UserCallbackClient;
import it.winsome.common.service.interfaces.UserCallbackServer;
import it.winsome.common.WinsomeHelper;
import it.winsome.common.validation.Validator;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.NoSuchFileException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Core of a client, this manages connections, and every interaction the client has with the server.
 * Input management is not handled by this class
 */
public class ClientApplication implements AutoCloseable {
    private final static Map<String, BiConsumer<ClientApplication, String[]>> mapDispatcher;
    private ClientConnector clientConnector;
    private NetMessage cachedMessage;
    private NetMessage cachedMessageReceive;

    private final ClientSocialState clientState = new ClientSocialState();
    private User currentUser;

    private final ClientConfiguration configuration = new ClientConfiguration();

    private final AtomicBoolean walletUpdatable = new AtomicBoolean();
    private UserCallbackServer regUserSrv;

    public ClientApplication() {

    }

    /**
     * Check if the wallet has been updated, if true then reset the flag
     * @return is the wallet updated
     */
    public boolean consumeWalletNotification() {
        if(walletUpdatable.get()) {
            walletUpdatable.set(false);
            return true;
        }

        return false;
    }

    /**
     * Load the configuration if the file exists otherwise throw an exception
     * @param path file location
     * @throws IOException io exception
     */
    public void loadConfiguration(String path) throws IOException {
        try {
            configuration.loadFromJson(path);
            WinsomeHelper.printlnDebug("Loaded client configuration successfully!");
        } catch (IOException e) {
            if((e instanceof NoSuchFileException)) {
                printError("Configuration not found at path ./client_config.json!");
                if(ClientConfiguration.generateDefaultFile("./client_config.json")) {
                    printError("Generated a client configuration template!");
                }
            } else {
                e.printStackTrace();
            }

            throw e;
        }
    }

    /**
     * Initialize the client and check for server registry
     * @throws RemoteException if the server is not found
     * @throws NotBoundException if the service name was not found
     */
    public void initApplication() throws RemoteException, NotBoundException {
        clientConnector = new ClientConnector(configuration.serverTcpAddress,
                configuration.serverTcpPort);

        Registry r = LocateRegistry.getRegistry(configuration.rmiServicePort);
        regUserSrv = (UserCallbackServer) r.lookup(configuration.rmiServiceName);
    }

    /**
     * Send a command to the application
     * @param command command name
     * @param args arguments used by the command
     */
    public void sendCommand(String command, String[] args) {
        BiConsumer<ClientApplication, String[]> consumer =
                mapDispatcher.getOrDefault(command, ClientApplication::handleUnknown);

        try {
            consumer.accept(this, args);
        } catch(InvalidParameterException e) {
            printError("Parameter invalid: " + e.getMessage());
        }
    }

    static {
        mapDispatcher = Collections.unmodifiableMap(new HashMap<String, BiConsumer<ClientApplication, String[]>>() {{
            put("register", ClientApplication::handleRegister);
            put("login", ClientApplication::handleLogin);
            put("logout", ClientApplication::handleLogout);
            put("list users", ClientApplication::handleListUsers);
            put("list followers", ClientApplication::handleListFollowers);
            put("list following", ClientApplication::handleListFollowing);
            put("follow", ClientApplication::handleFollow);
            put("unfollow", ClientApplication::handleUnfollowUser);
            put("blog", ClientApplication::handleViewBlog);
            put("post", ClientApplication::handleCreatePost);
            put("show feed", ClientApplication::handleShowFeed);
            put("show post", ClientApplication::handleShowPost);
            put("delete", ClientApplication::handleDeletePost);
            put("rewin", ClientApplication::handleRewinPost);
            put("rate", ClientApplication::handleRatePost);
            put("comment", ClientApplication::handleAddComment);
            put("wallet", ClientApplication::handleWallet);
            put("help", ClientApplication::handleHelp);
        }});
    }

    /**
     * Get the command list
     * @return command set
     */
    public static Set<String> getCommandsAvailable() {
        return mapDispatcher.keySet();
    }

    /**
     * Help command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleHelp(ClientApplication sender, String[] args) {
        printResponse("register username password [tags]\n" +
                "login username password\n" +
                "logout\n" +
                "list users\n" +
                "list followers\n" +
                "list following\n" +
                "follow username\n" +
                "unfollow username\n" +
                "blog [page]\n" +
                "post \"title\" \"content\"\n" +
                "show feed [page]\n" +
                "show post postId\n" +
                "delete postId\n" +
                "rewin postId\n" +
                "rate postId [+1 or -1]\n" +
                "comment postId \"content\"\n" +
                "wallet currencyName\n");
    }

    /**
     * Register command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleRegister(ClientApplication sender, String[] args) {
        String username = args[0];
        String password = WinsomeHelper.generateFromSHA256(args[1]);
        String[] tags = Arrays.copyOfRange(args, 2, args.length);

        Validator.validateUsername(username);
        Validator.validatePassword(password);
        Validator.validateTags(Arrays.asList(tags));

        try {
            User createdUser = sender.regUserSrv.registerUser(username, password, tags);
            printResponse("User created with username %s", createdUser.getUsername());
        } catch (InvalidParameterException ex) {
            printError(ex.toString());
        } catch (RemoteException e) {
            printError("RMI not working properly, did you call init? More: " + e.getMessage());
        }
    }

    /**
     * Login command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleLogin(ClientApplication sender, String[] args) {
        String username = args[0];
        String password = WinsomeHelper.generateFromSHA256(args[1]);

        Validator.validateUsername(username);
        Validator.validatePassword(password);

        if(sender.clientState.isLoggedIn()) {
            printError("You are already logged in, logout from your current session!");
            return;
        }

        if(!sender.clientConnector.isConnected()) {
            if(!sender.clientConnector.startTCP()) {
                printError("Could not connect to server!");
                return;
            }
        }

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage, NetMessageType.Login,
                        NetMessage.getStringSize(username, password))
                .writeString(username)
                .writeString(password);

        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.Login) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.UsernameNotExists) {
                    printError("Username does not exists, register yourself with 'register'!");
                } else if(result == NetResponseType.WrongPassword) {
                    printError("Password is invalid!");
                } else if(result == NetResponseType.ClientAlreadyLoggedIn) {
                    printError("You are already logged in, logout from your current session!");
                } else if(result == NetResponseType.Success) {
                    LoginUserDTO loginDTO = responseMessage.readObject(LoginUserDTO::netDeserialize);
                    loginDTO.user.setUsername(username);

                    sender.currentUser = loginDTO.user;
                    sender.clientState.setLogin(loginDTO.user);
                    UserCallbackClient stub = (UserCallbackClient)
                            UnicastRemoteObject.exportObject(sender.clientState, 0);
                    sender.regUserSrv.registerUserCallback(username, stub);

                    sender.clientConnector.startMulticast(
                            loginDTO.multicastIp, loginDTO.multicastPort,
                            () -> sender.walletUpdatable.compareAndSet(false, true));
                    printResponse("Welcome back %s, have fun here!", username);
                } else {
                    printError("Unexpected response from server!");
                }
            }
        } catch (SocketDisconnectedException e) {
            printError("Server probably unreachable!");
        } catch (UserNotExistsException e) {
            printError("Unexpected response from server!");
        } catch (RemoteException e) {
            printError("RMI not working properly, did you call init? More: " + e.getMessage());
        }
    }

    /**
     * Logout command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleLogout(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                NetMessageType.Logout, 0);

        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.Logout) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.Success) {
                    printResponse("See you later %s!", sender.currentUser.getUsername());
                } else if(result == NetResponseType.UsernameNotExists) {
                    printResponse("User %s was not found, probably deleted recently!", sender.currentUser.getUsername());
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }

                sender.clientState.logout();
                sender.regUserSrv.unregisterUserCallback(sender.currentUser.getUsername(), sender.clientState);
                UnicastRemoteObject.unexportObject(sender.clientState, true);
                sender.clientConnector.disconnect();
            }
        } catch (UserNotExistsException e) {
            printError("Unexpected response from server!");
        } catch (NoSuchObjectException e) {
            printError("RMI Object error. More: " + e.getMessage());
        } catch (RemoteException e) {
            printError("RMI not working properly, did you call init? More: " + e.getMessage());
        } catch (SocketDisconnectedException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * List Users command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleListUsers(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                NetMessageType.ListUser, 0);
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.ListUser) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.Success) {
                    ListUsersDTO data = responseMessage.readObject(ListUsersDTO::netDeserialize);
                    Iterator<User> usersIterator = data.userList.iterator();
                    StringBuilder outputString = new StringBuilder(200);
                    outputString.append("  ----------- Suggested Users -----------\n");
                    int count = 0;
                    while (usersIterator.hasNext()) {
                        count++;
                        User user = usersIterator.next();
                        (sender.currentUser.hasUserFollowed(user.getUsername()) ?
                                outputString.append("  ") : outputString.append("? ")) // space between results
                                .append(count).append(") ") // enumerate
                                .append(user.getUsername()).append("  ") // username
                                .append(WinsomeHelper.iteratorToString(user.getTagsIterator())); // tags
                        if(usersIterator.hasNext()) {
                            outputString.append('\n'); // new row
                        }
                    }

                    if(count == 0) {
                        outputString.append("         NONE          ");
                    }

                    printResponse(outputString.toString());
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }
        } catch (SocketDisconnectedException e) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * List Followers command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleListFollowers(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;

        Iterator<String> followingIterator = sender.currentUser.getFollowingIterator();
        StringBuilder outputString = new StringBuilder(100);
        outputString.append("  ----------- My Followers -----------\n");
        int count = 0;
        while (followingIterator.hasNext()) {
            count++;
            String follower = followingIterator.next();
            outputString.append("  ") // space between results
                    .append(count).append(") ").append(follower); // append actual result
            if(followingIterator.hasNext()) {
                outputString.append('\n'); // new row
            }
        }

        if(count == 0) {
            outputString.append("         NONE          ");
        }

        printResponse(outputString.toString());
    }

    /**
     * List Following command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleListFollowing(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;

        Iterator<String> followingIterator = sender.currentUser.getFollowedIterator();
        StringBuilder outputString = new StringBuilder(100);
        outputString.append("  ----------- My Followings -----------\n");
        int count = 0;
        while (followingIterator.hasNext()) {
            count++;
            String follower = followingIterator.next();
            outputString.append("  ") // space between results
                    .append(count).append(") ").append(follower); // append actual result
            if(followingIterator.hasNext()) {
                outputString.append('\n'); // new row
            }
        }

        if(count == 0) {
            outputString.append("         NONE          ");
        }

        printResponse(outputString.toString());
    }

    /**
     * Follow command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleFollow(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        String user = args[0];

        Validator.validateUsername(user);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.Follow, NetMessage.getStringSize(user))
                .writeString(user);

        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.Follow) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    sender.currentUser.addUserFollowed(user);
                    printResponse("You followed %s!", user);
                } else if(result == NetResponseType.UsernameNotExists) {
                    printResponse("User %s does not exist!", user);
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else if(result == NetResponseType.UserSelfFollow) {
                    printError("You can't follow yourself!");
                } else {
                    printError("Unexpected response from server!");
                }
            }
        } catch (SocketDisconnectedException e) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Unfollow command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleUnfollowUser(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        String user = args[0];

        Validator.validateUsername(user);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.Unfollow, NetMessage.getStringSize(user))
                .writeString(user);

        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.Unfollow) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    sender.currentUser.removeUserFollowed(user);
                    printResponse("You unfollowed %s!", user);
                } else if(result == NetResponseType.UsernameNotExists) {
                    printResponse("User %s does not exist!", user);
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else if(result == NetResponseType.UserSelfFollow) {
                    printError("You can't follow yourself!");
                } else if(result == NetResponseType.UserNotFollowed) {
                    printError("You don't follow %s!", user);
                } else {
                    printError("Unexpected response from server!");
                }
            }
        } catch (SocketDisconnectedException e) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Blog command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleViewBlog(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        int page = 0;
        if(args.length == 1) {
            page = Integer.parseInt(args[0]);
        }

        Validator.validatePage(page);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.ViewBlog, 4)
                .writeInt(page);
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.ViewBlog) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    ViewBlogDTO myBlog = responseMessage.readObject(ViewBlogDTO::netDeserialize);
                    StringBuilder outputString = new StringBuilder(500);
                    outputString.append("  ----------- My Blog -----------\n");
                    for(int i = 0; i < myBlog.postCount; i++) {
                        Post currentPost = myBlog.getPost(i);
                        currentPost.setUsername(sender.currentUser.getUsername());

                        outputString.append(i + 1).append(')')
                                .append(currentPost.isRewin() ? String.format(" (Rewin of @%s) ", currentPost.getOriginalPost().getUsername()) : " (Post) ")
                                .append(" Publisher @").append(currentPost.getUsername()).append("  [ID:").append(currentPost.getId()).append("]\n");
                        if(!currentPost.isRewin()) {
                            outputString.append(currentPost.getTitle()).append('\n')
                                    .append(currentPost.getContent()).append('\n');
                        } else {
                            Post rewin = currentPost.getOriginalPost();
                            outputString.append(rewin.getTitle()).append("  ")
                                    .append("  [ID:").append(rewin.getId()).append("] ").append(rewin.getCreationDate().toString()).append('\n');
                            outputString.append(rewin.getContent()).append('\n');
                        }

                        outputString.append("Published in ").append(currentPost.getCreationDate()).append('\n');
                        outputString.append("Comments: ").append(currentPost.getCommentCount())
                                .append(" | ").append("UPS: ").append(currentPost.getTotalUpvotes()).append(" DOWNS: ")
                                .append(currentPost.getTotalDownvotes());

                        if(i < myBlog.postCount - 1) {
                            outputString.append("\n\n");
                        }
                    }

                    printResponse(outputString.toString());
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }

        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Create Post command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleCreatePost(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        String title = args[0];
        String content = args[1];

        Validator.validatePostTitle(title);
        Validator.validatePostContent(content);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.CreatePost, NetMessage.getStringSize(title, content))
                .writeString(title)
                .writeString(content);

        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.CreatePost) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    int postId = responseMessage.readInt();
                    printResponse("Post created with id %d!", postId);
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }

        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Show feed command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleShowFeed(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        int page = 0;
        if(args.length == 1) {
            page = Integer.parseInt(args[0]);
        }

        Validator.validatePage(page);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.ShowFeed, 4)
                .writeInt(page);
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.ShowFeed) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    ShowFeedDTO myFeed = responseMessage.readObject(ShowFeedDTO::netDeserialize);
                    StringBuilder outputString = new StringBuilder(500);
                    outputString.append("  ----------- My Feed -----------\n");
                    for(int i = 0; i < myFeed.postCount; i++) {
                        Post currentPost = myFeed.getPost(i);

                        outputString.append(i + 1).append(')')
                                .append(currentPost.isRewin() ? String.format(" (Rewin of @%s) ", currentPost.getOriginalPost().getUsername()) : " (Post) ")
                                .append(" Publisher @").append(currentPost.getUsername()).append("  [ID:").append(currentPost.getId()).append("]\n");
                        if(!currentPost.isRewin()) {
                            outputString.append(currentPost.getTitle()).append('\n')
                                    .append(currentPost.getContent()).append('\n');
                        } else {
                            Post rewin = currentPost.getOriginalPost();
                            outputString.append(rewin.getTitle()).append("  ")
                                    .append("  [ID:").append(rewin.getId()).append("] ").append(rewin.getCreationDate().toString()).append('\n');
                            outputString.append(rewin.getContent()).append('\n');
                        }

                        outputString.append("Published in ").append(currentPost.getCreationDate()).append('\n');
                        outputString.append("Comments: ").append(currentPost.getCommentCount())
                                .append(" | ").append("UPS: ").append(currentPost.getTotalUpvotes()).append(" DOWNS: ")
                                .append(currentPost.getTotalDownvotes());

                        if(i < myFeed.postCount - 1) {
                            outputString.append("\n\n");
                        }
                    }

                    printResponse(outputString.toString());
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }

        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Show Post command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleShowPost(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        int postId = Integer.parseInt(args[0]);

        Validator.validatePostId(postId);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.ShowPost, 4)
                .writeInt(postId);
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.ShowPost) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    ShowPostDTO data = responseMessage.readObject(ShowPostDTO::netDeserialize);
                    Post currentPost = data.post;
                    if(currentPost == null) {
                        printError("Post with id %d does not exist!", postId);
                        return;
                    }

                    StringBuilder outputString = new StringBuilder(500);
                    outputString
                            .append(currentPost.isRewin() ? String.format(" (Rewin of @%s) ", currentPost.getOriginalPost().getUsername()) : " (Post) ")
                            .append(" @").append(currentPost.getUsername()).append("  [ID:").append(currentPost.getId()).append("]\n");
                    if(!currentPost.isRewin()) {
                        outputString.append(currentPost.getTitle()).append('\n')
                                .append(currentPost.getContent()).append('\n');
                    } else {
                        Post rewin = currentPost.getOriginalPost();
                        outputString.append(rewin.getTitle()).append("  ")
                                .append("  [ID:").append(rewin.getId()).append("] ").append(rewin.getCreationDate().toString()).append('\n');
                        outputString.append(rewin.getContent()).append('\n');
                    }

                    outputString.append("Published in ").append(currentPost.getCreationDate()).append('\n');
                    outputString.append("Comments: ").append(currentPost.getCommentCount())
                            .append(" | ").append("UPS: ").append(currentPost.getTotalUpvotes()).append(" DOWNS: ")
                            .append(currentPost.getTotalDownvotes()).append('\n');

                    Set<Comment> comments = currentPost.getComments();
                    if(comments.size() > 0) {
                        outputString.append("> [Comments] <\n");
                        Iterator<Comment> it = comments.iterator();
                        while(it.hasNext()) {
                            Comment comment = it.next();
                            outputString.append("@").append(comment.getOwner()).append(" says -> ").append(comment.getContent());
                            if(it.hasNext()) {
                                outputString.append('\n');
                            }
                        }
                    } else {
                        outputString.append("> [No Comments Available] <");
                    }

                    printResponse(outputString.toString());
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }

        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Delete post command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleDeletePost(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        int postId = Integer.parseInt(args[0]);

        Validator.validatePostId(postId);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.DeletePost, 4)
                .writeInt(postId);
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.DeletePost) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    printResponse("Post with id %d deleted successfully!", postId);
                } else if(result == NetResponseType.NotAuthorized) {
                    printError("You are not authorized to delete this post!");
                } else if(result == NetResponseType.EntityNotExists) {
                    printError("This post does not exists!");
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }

        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Rewin post command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleRewinPost(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        int postId = Integer.parseInt(args[0]);

        Validator.validatePostId(postId);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.RewinPost, 4)
                .writeInt(postId);
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.RewinPost) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    int rewinId = responseMessage.readInt();
                    printResponse("Post rewinned with id %d!", rewinId);
                } else if(result == NetResponseType.NotAuthorized) {
                    printError("You are not authorized to delete this post!");
                } else if(result == NetResponseType.EntityNotExists) {
                    printError("This post does not exists!");
                } else if(result == NetResponseType.UserSelfRewin) {
                    printError("You cannot rewin your own post!");
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }

        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Rate post command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleRatePost(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        int postId = Integer.parseInt(args[0]);
        VoteType vote = VoteType.fromString(args[1]);

        Validator.validatePostId(postId);
        Validator.validateVoteType(args[1]);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.RatePost, 12)
                .writeInt(postId)
                .writeInt(vote.getId())
                .writeInt(VotableType.Post.getId());
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.RatePost) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    printResponse("You gave a %s to this post!", vote.toString());
                } else if(result == NetResponseType.UserSelfVote) {
                    printError("You cannot vote to your own post!");
                } else if(result == NetResponseType.UserAlreadyVoted) {
                    printError("You already voted this post!");
                } else if(result == NetResponseType.PostNotInFeed) {
                    printError("This post is not in you feed!");
                } else if(result == NetResponseType.EntityNotExists) {
                    printError("This post does not exists!");
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }

        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Add comment command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleAddComment(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        int postId = Integer.parseInt(args[0]);
        String comment = args[1];

        Validator.validatePostId(postId);
        Validator.validateCommentContent(comment);

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.CreateComment, 4 + NetMessage.getStringSize(comment))
                .writeInt(postId)
                .writeString(comment);
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.CreateComment) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    int commentId = responseMessage.readInt();
                    printResponse("Comment published with id %d!", commentId);
                } else if(result == NetResponseType.UserSelfComment) {
                    printError("You cannot comment to your own post!");
                } else if(result == NetResponseType.PostNotInFeed) {
                    printError("This post is not in you feed!");
                } else if(result == NetResponseType.EntityNotExists) {
                    printError("This post does not exist!");
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }
        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Wallet command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleWallet(ClientApplication sender, String[] args) {
        if(sender.checkServerConnection() || sender.checkLogin()) return;
        CurrencyType currencyType = CurrencyType.Winsome;
        if(args.length > 0) {
            Validator.validateCurrencyType(args[0]);

            currencyType = CurrencyType.fromString(args[0]);
        }

        sender.cachedMessage = NetMessage.reuseWritableNetMessageOrCreate(sender.cachedMessage,
                        NetMessageType.Wallet, 4)
                .writeInt(currencyType.getId());
        try {
            NetMessage responseMessage = sender.sendAndAwaitResponse();
            if(responseMessage.getType() == NetMessageType.Wallet) {
                NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                if(result == NetResponseType.InvalidParameters) {
                    String message = responseMessage.readString();
                    printError(message);
                } else if(result == NetResponseType.Success) {
                    GetWalletDTO dto = responseMessage.readObject(GetWalletDTO::netDeserialize);
                    StringBuilder builder = new StringBuilder(400);
                    builder.append(String.format("------- Wallet in %s --------\n", currencyType))
                            .append("Amount: ").append(dto.wallet.getAmount()).append('\n')
                            .append("Transactions: [\n");
                    dto.wallet.getTransactions().forEach(x -> {
                        builder.append("Amount :").append(x.amount)
                                .append(" - Date : ").append(x.time).append('\n');
                    });
                    builder.append("]");
                    printResponse(builder.toString());
                } else if(result == NetResponseType.InternalError) {
                    printError("Internal error!");
                } else if(result == NetResponseType.ClientNotLoggedIn) {
                    printError("You are not logged in yet!");
                } else {
                    printError("Unexpected response from server!");
                }
            }

        } catch(SocketDisconnectedException ex) {
            printError("Server probably unreachable!");
        }
    }

    /**
     * Unknown command execution
     * @param sender client application
     * @param args arguments
     */
    private static void handleUnknown(ClientApplication sender, String[] args) {
        printError("Unknown command!");
    }

    /**
     * Check if the client is connected to the server
     * @return false if connected
     */
    private boolean checkServerConnection() {
        if(clientConnector == null || !clientConnector.isConnected()) {
            printError("You are not connected to the server, try login before using other functionalities!");
            return true;
        }

        return false;
    }

    /**
     * Check if the client is logged in
     * @return false if logged in
     */
    private boolean checkLogin() {
        if(!clientState.isLoggedIn()) {
            printError("You are not logged in, try login before using other functionalities!");
            return true;
        }

        return false;
    }

    /**
     * Send the message to the server and wait for a response
     * @return the response message
     * @throws SocketDisconnectedException if the client disconnected
     */
    private NetMessage sendAndAwaitResponse() throws SocketDisconnectedException {
        clientConnector.sendTcpMessage(cachedMessage);
        cachedMessageReceive = clientConnector.receiveTcpMessage(cachedMessageReceive);
        cachedMessageReceive.prepareRead();
        return cachedMessageReceive;
    }

    /**
     * Print response in a formatted way
     * @param format format string
     * @param args arguments
     */
    public static void printResponse(String format, Object... args) {
        System.out.printf(String.format("< %s\n\n", format), args);
    }

    /**
     * Print error in a formatted way
     * @param format format string
     * @param args arguments
     */
    public static void printError(String format, Object... args) {
        System.out.printf(String.format("! %s\n\n", format), args);
    }

    /**
     * Close the connection to the server
     */
    private void clearResources() {
        System.out.println("Closing...");

        if(clientConnector != null && clientConnector.isConnected()) {
            try {
                clientConnector.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() {
        clearResources();
    }
}