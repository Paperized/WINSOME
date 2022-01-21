package it.winsome.server.workers;

import it.winsome.common.dto.*;
import it.winsome.common.entity.Comment;
import it.winsome.common.entity.Post;
import it.winsome.common.entity.User;
import it.winsome.common.entity.enums.VotableType;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.exception.NoAuthorizationException;
import it.winsome.common.exception.SocketDisconnectedException;
import it.winsome.common.network.NetMessage;
import it.winsome.common.network.enums.NetMessageType;
import it.winsome.common.network.enums.NetResponseType;
import it.winsome.common.WinsomeHelper;
import it.winsome.server.session.ConnectionSession;
import it.winsome.server.ServerConnector;
import it.winsome.server.ServerMain;
import it.winsome.server.UserServiceImpl;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.rmi.RemoteException;
import java.util.*;
import java.util.function.Function;

import static it.winsome.common.network.enums.NetMessageType.*;

public class ReaderRequestHandler implements Runnable {
    private final static Map<NetMessageType, Function<ReaderRequestHandler, Boolean>> mapDispatcher;
    private static UserServiceImpl userService;
    private final ServerConnector server;
    private final ConnectionSession session;
    private final WritableByteChannel writableByteChannel;
    private final ReadableByteChannel readableByteChannel;
    private final SelectionKey key;
    private boolean didFinishWrite = true;

    public ReaderRequestHandler(ServerConnector server, SelectionKey key) {
        if(userService == null)
            userService = ServerMain.getUserServiceImpl();

        this.server = server;
        this.key = key;
        writableByteChannel = (WritableByteChannel) key.channel();
        readableByteChannel = (ReadableByteChannel) key.channel();
        session = (ConnectionSession) key.attachment();
        this.key.interestOps(0);
    }

    @Override
    public void run() {
        if(!readableByteChannel.isOpen()) {
            onClientDisconnected();
            System.out.println("Connection closed!");
            return;
        }

        NetMessage incomingMessage;
        try {
            incomingMessage = session.getReadableMessage();
            if(!incomingMessage.didStartReading()) {
                incomingMessage = NetMessage.fromChannel(incomingMessage, readableByteChannel);
                session.setReadableMessage(incomingMessage);
            } else {
                NetMessage.keepReadingFromChannel(incomingMessage, readableByteChannel);
            }

            if(!incomingMessage.isReadFully()) {
                System.out.println("Debug, message not read fully!");
                this.key.interestOps(SelectionKey.OP_READ);
                this.server.onHandlerFinish();
                return;
            }
        } catch (SocketDisconnectedException e) {
            onClientDisconnected();
            System.out.println("Connection closed!");
            return;
        }

        Function<ReaderRequestHandler, Boolean> fn = mapDispatcher.getOrDefault(incomingMessage.getType(),
                                                                        ReaderRequestHandler::handleUnknown);
        if(!fn.apply(this)) {
            System.out.println("Connection closed!");
            onClientDisconnected();
            return;
        }

        if(didFinishWrite)
            this.key.interestOps(SelectionKey.OP_READ);
        else
            this.key.interestOps(SelectionKey.OP_WRITE);
        this.server.onHandlerFinish();
    }

    static {
        mapDispatcher = Collections.unmodifiableMap(new HashMap<NetMessageType, Function<ReaderRequestHandler, Boolean>>() {{
            put(Login, ReaderRequestHandler::handleLogin);
            put(Follow, ReaderRequestHandler::handleFollow);
            put(Unfollow, ReaderRequestHandler::handleUnfollow);
            put(ListUser, ReaderRequestHandler::handleListUser);
            put(ViewBlog, ReaderRequestHandler::handleViewBlog);
            put(ShowFeed, ReaderRequestHandler::handleShowFeed);
            put(ShowPost, ReaderRequestHandler::handleShowPost);
            put(CreatePost, ReaderRequestHandler::handleCreatePost);
            put(DeletePost, ReaderRequestHandler::handleDeletePost);
            put(RewinPost, ReaderRequestHandler::handleRewinPost);
            put(RatePost, ReaderRequestHandler::handleRateEntity);
            put(RateComment, ReaderRequestHandler::handleRateEntity);
            put(CreateComment, ReaderRequestHandler::handleCreateComment);
            put(Logout, ReaderRequestHandler::handleLogout);
        }});
    }

    public static boolean handleLogin(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        String username = WinsomeHelper.normalizeUsername(incomingRequest.readString());
        String password = incomingRequest.readString();

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        User user;
        if((user = readerRequestHandler.hasAuthorizedUser()) != null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientAlreadyLoggedIn.getId());
            WinsomeHelper.printfDebug("Incoming login with username %s but client already logged as %s!", username, user.getUsername());
        } else {
            NetResponseType result = userService.makeSession(username, password, readerRequestHandler.key);

            if(result == NetResponseType.UsernameNotExists) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(result.getId());
                WinsomeHelper.printfDebug("Incoming login with username %s but does not exist!", username);
            } else if(result == NetResponseType.WrongPassword) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(result.getId());
                WinsomeHelper.printfDebug("Incoming login with %s:%s but wrong password!", username, password);
            } else if(result == NetResponseType.UserAlreadyLoggedIn) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(result.getId());
                WinsomeHelper.printfDebug("Incoming login with %s but a session already exist!", username, password);
            } else {
                LoginUserDTO dto = new LoginUserDTO((User) readerRequestHandler.key.attachment());
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(),
                        4 + LoginUserDTO.netSize(dto));

                response.writeInt(result.getId());
                response.writeObject(dto, LoginUserDTO::netSerialize);
                WinsomeHelper.printfDebug("Incoming login with %s:%s successfully!", username, password);
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleFollow(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        String toFollow = WinsomeHelper.normalizeUsername(incomingRequest.readString());

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
        User user;
        if((user = readerRequestHandler.hasAuthorizedUser()) == null) {
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printfDebug("Incoming follow to %s but client isn't logged in!", toFollow);
        } else {
            String username = user.getUsername();
            if(username.equalsIgnoreCase(toFollow)) {
                response.writeInt(NetResponseType.UserSelfFollow.getId());
                WinsomeHelper.printfDebug("Incoming follow from %s to itself!", username);
            } else {
                User followedUser = userService.getUserByUsername(toFollow);
                if(followedUser == null) {
                    response.writeInt(NetResponseType.UsernameNotExists.getId());
                    WinsomeHelper.printfDebug("Incoming follow to %s but it doesn't exist!", toFollow);
                } else {
                    user.addUserFollowed(toFollow);
                    if(followedUser.addUserFollowing(username)) {
                        try {
                            userService.notifyFollowAdded(username, toFollow);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    response.writeInt(NetResponseType.Success.getId());
                    WinsomeHelper.printfDebug("Incoming follow from %s to %s!", username, toFollow);
                }
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleUnfollow(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        String toFollow = WinsomeHelper.normalizeUsername(incomingRequest.readString());

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
        User user;
        if((user = readerRequestHandler.hasAuthorizedUser()) == null) {
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printfDebug("Incoming unfollow to %s but client isn't logged in!", toFollow);
        } else {
            String username = user.getUsername();
            if(username.equalsIgnoreCase(toFollow)) {
                response.writeInt(NetResponseType.UserSelfFollow.getId());
                WinsomeHelper.printfDebug("Incoming unfollow from %s to itself!", username);
            } else {
                User followedUser = userService.getUserByUsername(toFollow);
                if(followedUser == null) {
                    response.writeInt(NetResponseType.UsernameNotExists.getId());
                    WinsomeHelper.printfDebug("Incoming unfollow from %s to %s but it doesn't exist!", username, toFollow);
                } else {
                    if(!user.removeUserFollowed(toFollow)) {
                        response.writeInt(NetResponseType.UserNotFollowed.getId());
                        WinsomeHelper.printfDebug("Incoming unfollow from %s to %s but it is not being followed!", username, toFollow);
                    }
                    else {
                        if(followedUser.removeUserFollowing(username)) {
                            try {
                                userService.notifyFollowRemoved(username, toFollow);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }

                        response.writeInt(NetResponseType.Success.getId());
                        WinsomeHelper.printfDebug("Incoming unfollow from %s to %s!", username, toFollow);
                    }
                }
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleListUser(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        NetMessage response = readerRequestHandler.session.getWritableMessage();
        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming list users but client is not logged in!");
        } else {
            Set<String> interestsSet = loggedUser.getTags();
            List<User> similarUsers = userService.getSuggestedUsersByTags(interestsSet, loggedUser.getUsername());
            ListUsersDTO data = new ListUsersDTO(similarUsers);
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(),
                    ListUsersDTO.netSize(data) + 4);
            response.writeInt(NetResponseType.Success.getId());
            response.writeObject(data, ListUsersDTO::netSerialize);
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleShowPost(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        int postId = incomingRequest.readInt();

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        if((readerRequestHandler.hasAuthorizedUser()) == null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming show feed but client is not logged in!");
        } else {
            Post post = userService.getPost(postId);
            ShowPostDTO postDTO = new ShowPostDTO(post);
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(),
                    ShowPostDTO.netSize(postDTO) + 4);
            response.writeInt(NetResponseType.Success.getId());
            response.writeObject(postDTO, ShowPostDTO::netSerialize);
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleShowFeed(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        int pageIndex = incomingRequest.readInt();

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming show feed but client is not logged in!");
        } else {
            List<Post> userFeed = userService.getFeedByUsername(loggedUser.getUsername(), pageIndex);
            ShowFeedDTO feedDTO = new ShowFeedDTO(0);
            feedDTO.postList = userFeed;
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(),
                    ShowFeedDTO.netSize(feedDTO) + 4);
            response.writeInt(NetResponseType.Success.getId());
            response.writeObject(feedDTO, ShowFeedDTO::netSerialize);
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleViewBlog(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        int pageIndex = incomingRequest.readInt();

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming view blog but client is not logged in!");
        } else {
            List<Post> userBlog = userService.getBlogByUsername(loggedUser.getUsername(), pageIndex);
            ViewBlogDTO blogDTO = new ViewBlogDTO(0);
            blogDTO.postList = userBlog;
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(),
                    ViewBlogDTO.netSize(blogDTO) + 4);
            response.writeInt(NetResponseType.Success.getId());
            response.writeObject(blogDTO, ViewBlogDTO::netSerialize);
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleCreatePost(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        NetMessage response = readerRequestHandler.session.getWritableMessage();
        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming create post but client is not logged in!");
        } else {
            String title = incomingRequest.readString();
            String content = incomingRequest.readString();
            Post newPost = new Post(-1, loggedUser.getUsername(), title, content);
            NetResponseType result = userService.addPost(newPost);

            if(result != NetResponseType.Success) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(NetResponseType.OriginalPostNotExists.getId());
                WinsomeHelper.printfDebug("Incoming create post from %s but the original post does not exists!", loggedUser.getUsername());
            } else {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 8);
                response.writeInt(NetResponseType.Success.getId());
                response.writeInt(newPost.getId());
                WinsomeHelper.printfDebug("Incoming create post from %s ended with id %d!", loggedUser.getUsername(), newPost.getId());
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleDeletePost(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        int postId = incomingRequest.readInt();

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming delete post but client is not logged in!");
        } else {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);

            try {
                if(userService.removePostIfOwner(postId, loggedUser.getUsername())) {
                    response.writeInt(NetResponseType.Success.getId());
                    WinsomeHelper.printfDebug("Incoming delete post from %s with id %d successful!", loggedUser.getUsername(), postId);
                } else {
                    response.writeInt(NetResponseType.EntityNotExists.getId());
                    WinsomeHelper.printfDebug("Incoming delete post from %s with id %d but post does not exist!", loggedUser.getUsername(), postId);
                }
            } catch(NoAuthorizationException ex) {
                response.writeInt(NetResponseType.NotAuthorized.getId());
                WinsomeHelper.printfDebug("Incoming delete post from %s with id %d but post is not owned!", loggedUser.getUsername(), postId);
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleRewinPost(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        int postId = incomingRequest.readInt();

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming rewin post but client is not logged in!");
        } else {
            Post rewin = new Post(-1, loggedUser.getUsername(), null, null);
            rewin.setOriginalPost(new Post(postId));
            NetResponseType result = userService.addPost(rewin);
            if(result == NetResponseType.EntityNotExists) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(NetResponseType.EntityNotExists.getId());
                WinsomeHelper.printfDebug("Incoming rewin post from %s with id %d but post does not exist!", loggedUser.getUsername(), postId);
            } else if(result == NetResponseType.UserSelfRewin) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(NetResponseType.UserSelfRewin.getId());
                WinsomeHelper.printfDebug("Incoming rewin post from %s with id %d but post does not exist!", loggedUser.getUsername(), postId);
            } else {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 8);
                response.writeInt(NetResponseType.Success.getId());
                response.writeInt(rewin.getId());
                WinsomeHelper.printfDebug("Incoming rewin post from %s with id %d successful!", loggedUser.getUsername(), postId);
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleCreateComment(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        int postId = incomingRequest.readInt();
        String content = incomingRequest.readString();

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming create comment but client is not logged in!");
        } else {
            Comment comment = new Comment(-1, loggedUser.getUsername(), content);
            comment.setPostId(postId);

            NetResponseType result = userService.addComment(comment, loggedUser);
            if(result == NetResponseType.Success) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 8);
                response.writeInt(result.getId());
                response.writeInt(comment.getId());
                WinsomeHelper.printfDebug("Incoming create comment from %s ended with id %d!", loggedUser.getUsername(), comment.getId());
            } else if(result == NetResponseType.UserSelfComment) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(result.getId());
                WinsomeHelper.printfDebug("Incoming create comment from %s but it's own post!", loggedUser.getUsername());
            } else if(result == NetResponseType.PostNotInFeed) {
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(result.getId());
                WinsomeHelper.printfDebug("Incoming create comment from %s but it's not in the feed!", loggedUser.getUsername());
            } else if(result == NetResponseType.EntityNotExists){
                response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
                response.writeInt(result.getId());
                WinsomeHelper.printfDebug("Incoming create comment from %s but post id %d does not exist!", loggedUser.getUsername(), postId);
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleRateEntity(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        int postId = incomingRequest.readInt();
        VoteType type = VoteType.fromId(incomingRequest.readInt());
        VotableType entityType = VotableType.fromId(incomingRequest.readInt());

        NetMessage response = readerRequestHandler.session.getWritableMessage();
        response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
        if(type == null || entityType == null) {
            response.writeInt(NetResponseType.InvalidParameters.getId());
            WinsomeHelper.printfDebug("Incoming rate %s but client is not logged in!", entityType == null ? "??" : entityType.toString());
            return sendMessage(readerRequestHandler, incomingRequest);
        }

        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printfDebug("Incoming rate %s but client is not logged in!", entityType.toString());
        } else {
            NetResponseType result = userService.addVote(postId, entityType, type, loggedUser);
            response.writeInt(result.getId());

            if(result == NetResponseType.Success) {
                WinsomeHelper.printfDebug("Incoming rate %s from %s to %d with %s ended successfully!", entityType.toString(),
                        loggedUser.getUsername(), postId, type.toString());
            } else if(result == NetResponseType.UserAlreadyVoted) {
                WinsomeHelper.printfDebug("Incoming rate %s from %s to %d but already voted!", entityType.toString(),
                        loggedUser.getUsername(), postId);
            } else if(result == NetResponseType.UserSelfVote) {
                WinsomeHelper.printfDebug("Incoming rate %s from %s to %d but is a self vote!", entityType.toString(),
                        loggedUser.getUsername(), postId);
            } else if(result == NetResponseType.EntityNotExists) {
                WinsomeHelper.printfDebug("Incoming rate %s from %s to %d but it does not exist!", entityType.toString(),
                        loggedUser.getUsername(), postId);
            } else if(result == NetResponseType.PostNotInFeed) {
                WinsomeHelper.printfDebug("Incoming rate %s from %s to %d but is not contained in his feed!", entityType.toString(),
                        loggedUser.getUsername(), postId);
            } else if(result == NetResponseType.InvalidParameters) {
                WinsomeHelper.printfDebug("Incoming rate %s from %s to %d but vote type is invalid!", entityType.toString(),
                        loggedUser.getUsername(), postId);
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleLogout(ReaderRequestHandler readerRequestHandler) {
        NetMessage incomingRequest = readerRequestHandler.session.getReadableMessage();
        NetMessage response = readerRequestHandler.session.getWritableMessage();
        response = NetMessage.reuseWritableNetMessageOrCreate(response, incomingRequest.getType(), 4);
        User loggedUser;
        if((loggedUser = readerRequestHandler.hasAuthorizedUser()) == null) {
            response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
            WinsomeHelper.printlnDebug("Incoming logout but client is not logged in!");
        } else {
            if(userService.removeSession(readerRequestHandler.key)) {
                response.writeInt(NetResponseType.Success.getId());
                WinsomeHelper.printfDebug("Incoming logout with %s successfully!", loggedUser.getUsername());
            } else {
                response.writeInt(NetResponseType.ClientNotLoggedIn.getId());
                WinsomeHelper.printfDebug("Incoming logout with %s but client is not logged in!", loggedUser.getUsername());
            }
        }

        return sendMessage(readerRequestHandler, response);
    }

    public static boolean handleUnknown(ReaderRequestHandler readerRequestHandler) {
        NetMessage response = readerRequestHandler.session.getWritableMessage();
        response = NetMessage.reuseWritableNetMessageOrCreate(response, None, 4);
        response.writeInt(NetResponseType.InvalidParameters.getId());
        WinsomeHelper.printlnDebug("Incoming message has an unknown type!");

        return sendMessage(readerRequestHandler, response);
    }

    private User hasAuthorizedUser() {
        User loggedUser = ((ConnectionSession) key.attachment()).getUserLogged();
        if(loggedUser == null) {
            return null;
        }

        User checkedUser = userService.getUserByUsername(loggedUser.getUsername());
        if(checkedUser == null) {
            key.attach(null);
            return null;
        }

        return checkedUser;
    }

    private void onClientDisconnected() {
        userService.removeSession(key);
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean sendMessage(ReaderRequestHandler readerRequestHandler, NetMessage response) {
        try {
            readerRequestHandler.didFinishWrite = response.sendMessage(readerRequestHandler.writableByteChannel);
            readerRequestHandler.session.setWritableMessage(response);
            return true;
        } catch (SocketDisconnectedException e) {
            return false;
        }
    }
}
