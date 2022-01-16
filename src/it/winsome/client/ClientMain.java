package it.winsome.client;

import it.winsome.client.config.ClientConfiguration;
import it.winsome.common.dto.*;
import it.winsome.common.entity.Post;
import it.winsome.common.entity.User;
import it.winsome.common.entity.enums.VotableType;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.exception.NoTagsFoundException;
import it.winsome.common.exception.SocketDisconnectedException;
import it.winsome.common.exception.UserAlreadyExistsException;
import it.winsome.common.exception.UserNotExistsException;
import it.winsome.common.network.NetMessage;
import it.winsome.common.network.enums.NetMessageType;
import it.winsome.common.network.enums.NetResponseType;
import it.winsome.common.service.interfaces.UserCallback;
import it.winsome.common.service.interfaces.UserService;
import it.winsome.common.WinsomeHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.NoSuchFileException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Iterator;

public class ClientMain {
    private static final String err_command_args_count_eq = "%s needs %d arguments to be executed";
    private static final String err_command_args_count_ge = "%s needs atleast %d arguments to be executed";
    private static final String err_command_not_exists = "Command '%s' not exists";

    private final static BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in));
    private final static TcpClient tcpClient = new TcpClient();
    private static NetMessage cachedMessage;

    private static final ClientSocialState clientState = new ClientSocialState();
    private static User currentUser;

    private static final ClientConfiguration configuration = new ClientConfiguration();

    public static void main(String[] args) throws RemoteException, NotBoundException {
        WinsomeHelper.setDebugMode(true);
        Runtime.getRuntime().addShutdownHook(new Thread(ClientMain::onQuit));

        try {
            configuration.loadFromJson("./client_config.json");
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
            return;
        }

        Registry r = LocateRegistry.getRegistry(configuration.rmiServicePort);
        UserService regUserSrv = (UserService) r.lookup(configuration.rmiServiceName);

        String command = "";
        boolean isClosing = false;

        System.out.println("------ WINSOME Client -------");

        while (!isClosing && (command = readLine()) != null) {
            String[] commandSplitted = command.split(" ");
            switch (commandSplitted[0]) {
                case "test": {
                    if(!checkParamsEqCount(commandSplitted, 0)) continue;
                    try {
                        String psw = WinsomeHelper.generateFromSHA256("download99");
                        User newUser = regUserSrv.registerUser("ghfjy787", psw, new String[] { "pesca", "programmazione", "film", "LoL", "youtube", "anime", "manga" });
                        WinsomeHelper.printlnDebug(newUser.toString());
                        newUser = regUserSrv.registerUser("pippo99", psw, new String[] { "LoL", "agricoltura", "vino", "manga" });
                        WinsomeHelper.printlnDebug(newUser.toString());
                        newUser = regUserSrv.registerUser("gianmarco", psw, new String[] { "Film", "programmazione", "LoL", "youtube" });
                        WinsomeHelper.printlnDebug(newUser.toString());
                        newUser = regUserSrv.registerUser("piero", psw, new String[] { "crypto", "youtube", "film" });
                        WinsomeHelper.printlnDebug(newUser.toString());
                        newUser = regUserSrv.registerUser("claoclao", psw, new String[] { "anime", "manga", "un cazzo" });
                        WinsomeHelper.printlnDebug(newUser.toString());
                    } catch (Exception ex) {
                        printResponse(ex.toString());
                    }

                    break;
                } // done
                case "register": {
                    if(!checkParamsGeCount(commandSplitted, 3)) continue;
                    String username = commandSplitted[1];
                    String password = WinsomeHelper.generateFromSHA256(commandSplitted[2]);
                    String[] tags = Arrays.copyOfRange(commandSplitted, 3, commandSplitted.length);

                    try {
                        User createdUser = regUserSrv.registerUser(username, password, tags);
                        WinsomeHelper.printlnDebug(createdUser.toString());
                    } catch (UserAlreadyExistsException ex) {
                        printResponse("Username %s already exists!", username);
                    } catch(NoTagsFoundException ex) {
                        printResponse("Atleast 1 tag is needed!");
                    }

                    break;
                } // done
                case "login": {
                    if(!checkParamsEqCount(commandSplitted, 2)) continue;
                    String username = commandSplitted[1];
                    String password = WinsomeHelper.generateFromSHA256(commandSplitted[2]);

                    if(clientState.isLoggedIn()) {
                        printError("You are already logged in, logout from your current session!");
                        break;
                    }

                    if(!tcpClient.isConnected()) {
                        if(!tcpClient.connect(configuration.serverTcpAddress, configuration.serverTcpPort)) {
                            printError("Could not connect to server!");
                            break;
                        }
                    }

                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.Login,
                            NetMessage.getStringSize(username, password))
                            .writeString(username)
                            .writeString(password);

                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.Login) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.UsernameNotExists) {
                                printError("Username does not exists, register yourself with 'register'!");
                            } else if(result == NetResponseType.WrongPassword) {
                                printError("Password is invalid!");
                            } else if(result == NetResponseType.ClientAlreadyLoggedIn) {
                                printError("You are already logged in, logout from your current session!");
                            } else if(result == NetResponseType.Success) {
                                LoginUserDTO loginDTO = responseMessage.readObject(LoginUserDTO::netDeserialize);
                                loginDTO.user.setUsername(username);

                                currentUser = loginDTO.user;
                                clientState.setLogin(loginDTO.user);
                                UserCallback stub = (UserCallback)
                                        UnicastRemoteObject.exportObject(clientState, 0);
                                regUserSrv.registerUserCallback(username, stub);
                                printResponse("Welcome back %s, have fun here!", username);
                            } else {
                                printError("Unexpected response from server!");
                            }
                        }
                    } catch (SocketDisconnectedException e) {
                        e.printStackTrace();
                    } catch (UserNotExistsException e) {
                        printError("Unexpected response from server!");
                    }
                    break;
                } // done
                case "logout": {
                    if(!checkParamsEqCount(commandSplitted, 0)) continue;
                    if(checkServerConnection() || checkLogin()) break;

                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.Logout, 0);

                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.Logout) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
                                printResponse("See you later %s!", currentUser.getUsername());
                            } else if(result == NetResponseType.UsernameNotExists) {
                                printResponse("User %s was not found, probably deleted recently!", currentUser.getUsername());
                            } else if(result == NetResponseType.ClientNotLoggedIn) {
                                printError("You are not logged in yet!");
                            } else {
                                printError("Unexpected response from server!");
                            }

                            clientState.logout();
                            regUserSrv.unregisterUserCallback(currentUser.getUsername(), clientState);
                            UnicastRemoteObject.unexportObject(clientState, true);
                        }
                    } catch (SocketDisconnectedException e) {
                        e.printStackTrace();
                    } catch (UserNotExistsException e) {
                        printError("Unexpected response from server!");
                    }
                    break;
                } // done
                case "list": {
                    if(!checkParamsEqCount(commandSplitted, 0, 2)) continue;

                    switch(commandSplitted[1]) {
                        case "users": {
                            if(checkServerConnection() || checkLogin()) break;

                            cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.ListUser, 0);
                            try {
                                cachedMessage.sendMessage(tcpClient);
                                NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
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
                                            (currentUser.hasUserFollowed(user.getUsername()) ?
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
                                e.printStackTrace();
                            }

                            break;
                        }
                        case "followers": {
                            if(checkServerConnection() || checkLogin()) break;

                            Iterator<String> followingIterator = currentUser.getFollowingIterator();
                            StringBuilder outputString = new StringBuilder(100);
                            outputString.append("  ----------- My Followers -----------\n");
                            int count = 0;
                            int elementsPerRow = 2;
                            while (followingIterator.hasNext()) {
                                count++;
                                String follower = followingIterator.next();
                                outputString.append("  ") // space between results
                                        .append(count).append(") ").append(follower); // append actual result
                                if(count % elementsPerRow == 0 && followingIterator.hasNext()) {
                                    outputString.append('\n'); // new row
                                }
                            }

                            if(count == 0) {
                                outputString.append("         NONE          ");
                            }

                            printResponse(outputString.toString());
                            break;
                        }
                        case "following": {
                            if(checkServerConnection() || checkLogin()) break;

                            Iterator<String> followingIterator = currentUser.getFollowedIterator();
                            StringBuilder outputString = new StringBuilder(100);
                            outputString.append("  ----------- My Followings -----------\n");
                            int count = 0;
                            int elementsPerRow = 2;
                            while (followingIterator.hasNext()) {
                                count++;
                                String follower = followingIterator.next();
                                outputString.append("  ") // space between results
                                        .append(count).append(") ").append(follower); // append actual result
                                if(count % elementsPerRow == 0 && followingIterator.hasNext()) {
                                    outputString.append('\n'); // new row
                                }
                            }

                            if(count == 0) {
                                outputString.append("         NONE          ");
                            }

                            printResponse(outputString.toString());
                            break;
                        }
                        default:
                            printError(err_command_not_exists, "list " + commandSplitted[1]);
                            continue;
                    }
                    break;
                } // done
                case "follow": {
                    if(!checkParamsEqCount(commandSplitted, 1)) continue;
                    if(checkServerConnection() || checkLogin()) break;
                    String user = commandSplitted[1];

                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.Follow,
                            NetMessage.getStringSize(user))
                            .writeString(user);

                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.Follow) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
                                currentUser.addUserFollowed(user);
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
                        e.printStackTrace();
                    }
                    break;
                } // done
                case "unfollowUser": {
                    if(!checkParamsEqCount(commandSplitted, 1)) continue;
                    if(checkServerConnection() || checkLogin()) break;
                    String user = commandSplitted[1];

                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.Unfollow,
                            NetMessage.getStringSize(user))
                            .writeString(user);

                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.Unfollow) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
                                currentUser.removeUserFollowed(user);
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
                        e.printStackTrace();
                    }
                    break;
                } // done
                case "viewBlog": {
                    if(checkServerConnection() || checkLogin()) break;
                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.ViewBlog, 4)
                            .writeInt(0);
                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.ViewBlog) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
                                ViewBlogDTO myBlog = responseMessage.readObject(ViewBlogDTO::netDeserialize);
                                StringBuilder outputString = new StringBuilder(500);
                                outputString.append("  ----------- My Blog -----------\n");
                                for(int i = 0; i < myBlog.postCount; i++) {
                                    Post currentPost = myBlog.getPost(i);
                                    currentPost.setUsername(currentUser.getUsername());

                                    outputString.append(i + 1).append(')')
                                            .append(currentPost.isRewin() ? "  REWIN " : String.format("  %s ", currentPost.getTitle()))
                                            .append(currentPost.getCreationDate().toString())
                                            .append(" @").append(currentPost.getUsername()).append("  [ID:").append(currentPost.getId()).append("]\n");
                                    if(!currentPost.isRewin()) {
                                        outputString.append(currentPost.getContent()).append('\n');
                                    } else {
                                        Post rewin = currentPost.getOriginalPost();
                                        outputString.append("-->").append(rewin.getTitle()).append("  ").append(rewin.getCreationDate().toString())
                                                .append(" @").append(rewin.getUsername()).append("  [ID:").append(rewin.getId()).append(']').append('\n');
                                        outputString.append("-->").append(rewin.getContent().replaceAll("\n", "\n-->")).append('\n');
                                    }

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
                        ex.printStackTrace();
                    }
                    break;
                } // done
                case "createPost": {
                    if(!checkParamsEqCount(commandSplitted, 2)) continue;
                    if(checkServerConnection() || checkLogin()) break;
                    String title = commandSplitted[1];
                    String content = commandSplitted[2];

                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.CreatePost,
                            NetMessage.getStringSize(title, content))
                            .writeString(title)
                            .writeString(content);

                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.CreatePost) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
                                int postId = responseMessage.readInt();
                                printResponse("Post created with id %d!", postId);
                            } else if(result == NetResponseType.ClientNotLoggedIn) {
                                printError("You are not logged in yet!");
                            } else {
                                printError("Unexpected response from server!");
                            }
                        }

                    } catch(SocketDisconnectedException ex) {
                        ex.printStackTrace();
                    }

                    break;
                } // done
                case "show": {
                    if(!checkParamsGeCount(commandSplitted, 0, 2)) continue;

                    switch(commandSplitted[1]) {
                        case "feed": {
                            if(checkServerConnection() || checkLogin()) break;
                            cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.ShowFeed, 4)
                                    .writeInt(0);
                            try {
                                cachedMessage.sendMessage(tcpClient);
                                NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                                if(responseMessage.getType() == NetMessageType.ShowFeed) {
                                    NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                                    if(result == NetResponseType.Success) {
                                        ShowFeedDTO myFeed = responseMessage.readObject(ShowFeedDTO::netDeserialize);
                                        StringBuilder outputString = new StringBuilder(500);
                                        outputString.append("  ----------- My Feed -----------\n");
                                        for(int i = 0; i < myFeed.postCount; i++) {
                                            Post currentPost = myFeed.getPost(i);

                                            outputString.append(i + 1).append(')')
                                                    .append(currentPost.isRewin() ? "  REWIN " : String.format("  %s ", currentPost.getTitle()))
                                                    .append(currentPost.getCreationDate().toString())
                                                    .append(" @").append(currentPost.getUsername()).append("  [ID:").append(currentPost.getId()).append("]\n");
                                            if(!currentPost.isRewin()) {
                                                outputString.append(currentPost.getContent()).append('\n');
                                            } else {
                                                Post rewin = currentPost.getOriginalPost();
                                                outputString.append("--> ").append(rewin.getTitle()).append("  ").append(rewin.getCreationDate().toString())
                                                        .append(" @").append(rewin.getUsername()).append("  [ID:").append(rewin.getId()).append(']').append('\n');
                                                outputString.append("-->").append(rewin.getContent().replaceAll("\n", "\n-->")).append('\n');
                                            }

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
                                ex.printStackTrace();
                            }
                            break;
                        }
                        case "post": {
                            if(!checkParamsEqCount(commandSplitted, 1, 2)) continue;
                            if(checkServerConnection() || checkLogin()) break;

                            int postId = Integer.parseInt(commandSplitted[2]);
                            cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.ShowPost, 4)
                                    .writeInt(postId);
                            try {
                                cachedMessage.sendMessage(tcpClient);
                                NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                                if(responseMessage.getType() == NetMessageType.ShowPost) {
                                    NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                                    if(result == NetResponseType.Success) {
                                        ShowPostDTO data = responseMessage.readObject(ShowPostDTO::netDeserialize);
                                        Post currentPost = data.post;
                                        StringBuilder outputString = new StringBuilder(500);
                                        outputString.append("  ").append(currentPost.getTitle()).append("  ").append(currentPost.getCreationDate().toString())
                                                .append('@').append(currentPost.getUsername()).append("  [ID:").append(currentPost.getId()).append("]\n");
                                        if(!currentPost.isRewin()) {
                                            outputString.append(currentPost.getContent()).append('\n');
                                        } else {
                                            Post rewin = currentPost.getOriginalPost();
                                            outputString.append("RW --> ").append(rewin.getTitle()).append("  ").append(rewin.getCreationDate().toString())
                                                    .append(" @").append(rewin.getUsername()).append("  [ID:").append(rewin.getId()).append(']').append('\n');
                                            outputString.append("   -->").append(rewin.getContent().replaceAll("\n", "\n   -->")).append('\n');
                                        }

                                        outputString.append("Comments: ").append(currentPost.getCommentCount())
                                                .append(" | ").append("UPS: ").append(currentPost.getTotalUpvotes()).append(" DOWNS: ")
                                                .append(currentPost.getTotalDownvotes());

                                        printResponse(outputString.toString());
                                    } else if(result == NetResponseType.ClientNotLoggedIn) {
                                        printError("You are not logged in yet!");
                                    } else {
                                        printError("Unexpected response from server!");
                                    }
                                }

                            } catch(SocketDisconnectedException ex) {
                                ex.printStackTrace();
                            }
                            break;
                        }
                        default:
                            printError(err_command_not_exists, "show " + commandSplitted[1]);
                            break;
                    }
                    break;
                } // done
                case "deletePost": {
                    if(!checkParamsEqCount(commandSplitted, 1)) continue;
                    if(checkServerConnection() || checkLogin()) continue;
                    int postId = Integer.parseInt(commandSplitted[1]);
                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.DeletePost, 4)
                            .writeInt(postId);
                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.ShowPost) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
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
                        ex.printStackTrace();
                    }
                    break;
                } // done
                case "rewinPost": {
                    if(!checkParamsEqCount(commandSplitted, 2)) continue;
                    if(checkServerConnection() || checkLogin()) continue;

                    int postId = Integer.parseInt(commandSplitted[1]);
                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.RewinPost, 4)
                            .writeInt(postId);
                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.RewinPost) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
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
                        ex.printStackTrace();
                    }
                    break;
                } // done
                case "ratePost": {
                    if(!checkParamsEqCount(commandSplitted, 2)) continue;
                    if(checkServerConnection() || checkLogin()) continue;
                    int postId = Integer.parseInt(commandSplitted[1]);
                    VoteType vote = VoteType.fromString(commandSplitted[2]);
                    if(vote == null) {
                        printError("Vote can be either \"+1\" or \"-1\"");
                        break;
                    }

                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.RatePost, 12)
                            .writeInt(postId)
                            .writeInt(vote.getId())
                            .writeInt(VotableType.Post.getId());
                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.RatePost) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
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
                        ex.printStackTrace();
                    }
                    break;
                } // done
                case "addComment": {
                    if(!checkParamsEqCount(commandSplitted, 2)) continue;
                    if(checkServerConnection() || checkLogin()) continue;
                    int postId = Integer.parseInt(commandSplitted[1]);
                    String comment = commandSplitted[2];

                    cachedMessage = NetMessage.reuseNetMessageOrCreate(cachedMessage, NetMessageType.CreateComment, 4 + NetMessage.getStringSize(comment))
                            .writeInt(postId)
                            .writeString(comment);
                    try {
                        cachedMessage.sendMessage(tcpClient);
                        NetMessage responseMessage = NetMessage.fromHandler(tcpClient);
                        if(responseMessage.getType() == NetMessageType.CreateComment) {
                            NetResponseType result = NetResponseType.fromId(responseMessage.readInt());
                            if(result == NetResponseType.Success) {
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
                        ex.printStackTrace();
                    }
                    break;
                } // done
                case "getWallet": {
                    if(!checkParamsEqCount(commandSplitted, 0)) continue;

                    if(checkServerConnection()) break;

                    WinsomeHelper.printlnDebug("getWallet()");
                    break;
                }
                case "getWalletInBitcoin": {
                    if(!checkParamsEqCount(commandSplitted, 0)) continue;

                    if(checkServerConnection()) break;

                    WinsomeHelper.printlnDebug("< getWalletInBitcoin()");
                    break;
                }
                case "quit":
                    isClosing = true;
                    break;
                default:
                    printError(err_command_not_exists, commandSplitted[0]);
            }
        }

        if (command == null) {
            return;
        }

        System.out.println("WINSOME closing...");
    }

    private static String readLine() {
        try {
            System.out.print("> ");
            return reader.readLine();
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static boolean checkServerConnection() {
        if(!tcpClient.isConnected()) {
            printError("You are not connected to the server, try login before using other functionalities!");
            return true;
        }

        return false;
    }

    private static boolean checkLogin() {
        if(!clientState.isLoggedIn()) {
            printError("You are not logged in, try login before using other functionalities!");
            return true;
        }

        return false;
    }

    private static void printResponse(String format, Object... args) {
        System.out.printf(String.format("< %s\n", format), args);
    }

    private static void printError(String format, Object... args) {
        System.out.printf(String.format("! %s\n", format), args);
    }

    private static boolean checkParamsEqCount(String[] arr, int count, int functionNameRange) {
        if(arr.length - functionNameRange == count) {
            return true;
        }

        if(functionNameRange > arr.length) {
            if(functionNameRange == 1)
                printError(err_command_not_exists, arr[0]);
            else
                printError(err_command_not_exists, String.join(" ", Arrays.copyOfRange(arr, 0, arr.length)));

            return false;
        }

        printResponse(err_command_args_count_eq, String.join(" ", Arrays.copyOfRange(arr, 0, functionNameRange)), count);
        return false;
    }

    private static boolean checkParamsGeCount(String[] arr, int count, int functionNameRange) {
        if(arr.length - functionNameRange >= count) {
            return true;
        }

        if(functionNameRange > arr.length) {
            if(functionNameRange == 1)
                printError(err_command_not_exists, arr[0]);
            else
                printError(err_command_not_exists, String.join(" ", Arrays.copyOfRange(arr, 0, arr.length)));

            return false;
        }

        printResponse(err_command_args_count_ge, String.join(" ", Arrays.copyOfRange(arr, 0, functionNameRange)), count);
        return false;
    }

    private static boolean checkParamsEqCount(String[] arr, int count) {
        return checkParamsEqCount(arr, count, 1);
    }

    private static boolean checkParamsGeCount(String[] arr, int count) {
        return checkParamsGeCount(arr, count, 1);
    }

    private static void onQuit() {
        System.out.println("Closing...");

        if(tcpClient.isConnected()) {
            try {
                tcpClient.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}