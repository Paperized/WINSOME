package it.winsome.server.workers;

import it.winsome.common.WinsomeHelper;
import it.winsome.common.entity.Comment;
import it.winsome.common.entity.Post;
import it.winsome.common.entity.User;
import it.winsome.common.entity.Vote;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.network.NetMessage;
import it.winsome.common.network.enums.NetMessageType;
import it.winsome.server.ServerMain;
import it.winsome.server.UserServiceImpl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class RecalculateWallet implements Runnable {
    private UserServiceImpl userService;
    private DatagramSocket multicastSocket;
    private InetAddress multicastAddress;
    private int multicastPort;
    private NetMessage writableMessage;
    private int percentageAuthor;

    public RecalculateWallet(String multicastIp, int multicastPort, int percentageAuthor) throws IOException {
        userService = ServerMain.getUserServiceImpl();
        multicastSocket = new DatagramSocket();
        multicastAddress = InetAddress.getByName(multicastIp);
        this.multicastPort = multicastPort;
        this.percentageAuthor = percentageAuthor;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        HashMap<String, Double> rewards = new HashMap<>();
        // calculate rewards
        Collection<Post> posts = userService.getPostsResource();
        for(Post post : posts) {
            calculateIterationAmount(post, rewards);
        }
        userService.unlockPosts();

        //update users wallet
        boolean didUpdate = rewards.size() > 0;
        if(didUpdate) {
            Map<String, User> users = userService.getUsersResource();
            for(Map.Entry<String, Double> entry : rewards.entrySet()) {
                User user = users.get(entry.getKey());
                if(user == null) continue;
                user.addWalletAmount(entry.getValue());
            }
            userService.unlockUsers();
        }

        // recalculate
        long elapsed = System.currentTimeMillis() - start;
        WinsomeHelper.printfDebug("Recalculated wallets %dms!", elapsed);
        if(!didUpdate) return;

        writableMessage = NetMessage.reuseWritableNetMessageOrCreate(writableMessage, NetMessageType.NotifyWallet, 0);
        sendMulticastMessage();
    }

    private void calculateIterationAmount(Post post, Map<String, Double> interactingUsers) {
        int it = post.getCurrentIteration() + 1;

        Set<String> contributors = new HashSet<>();
        double votesScore = 0;
        for(Vote vote : post.getVotes()) {
            if(!vote.isNeedIteration()) continue;
            vote.setNeedIteration(true);
            contributors.add(vote.getFrom());
            votesScore += vote.getType() == VoteType.UP ? 1 : -1;
        }
        votesScore = Math.min(votesScore, 0);

        double commentsScore = 0;
        Map<String, Integer> commentsCount = new HashMap<>();
        for(Comment comment : post.getComments()) {
            if(commentsCount.containsKey(comment.getOwner())) {
                commentsCount.replace(comment.getOwner(), commentsCount.get(comment.getOwner()) + 1);
            } else {
                commentsCount.put(comment.getOwner(), 1);
            }
        }

        for(Comment comment : post.getComments()) {
            if(!comment.isNeedIteration()) continue;
            comment.setNeedIteration(false);
            contributors.add(comment.getOwner());
            commentsScore += (2/
                    (1 + Math.pow(
                            Math.E, -(commentsCount.get(comment.getOwner()) - 1)
            )));
        }

        double total = (Math.log(votesScore + 1) + Math.log(commentsScore + 1)) / it;
        if(contributors.size() == 0)
            return;
        double authorAmount = total * percentageAuthor / 100;
        double othersAmount = (total - authorAmount) / (double)contributors.size();
        if(interactingUsers.containsKey(post.getUsername())) {
            interactingUsers.replace(post.getUsername(),
                    interactingUsers.get(post.getUsername()) + authorAmount);
        } else {
            interactingUsers.put(post.getUsername(), authorAmount);
        }

        for(String contributor : contributors) {
            if(interactingUsers.containsKey(contributor)) {
                interactingUsers.replace(contributor,
                        interactingUsers.get(contributor) + othersAmount);
            } else {
                interactingUsers.put(contributor, othersAmount);
            }
        }
    }

    private void sendMulticastMessage() {
        try {
            ByteBuffer buf = writableMessage.getByteBuffer();
            DatagramPacket dp = new DatagramPacket(buf.array(), writableMessage.getMessageLength(),
                    multicastAddress, multicastPort);
            multicastSocket.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutdownMulticast() {
        multicastSocket.close();
    }
}
