package it.winsome.server.workers;

import it.winsome.common.SynchronizedObject;
import it.winsome.common.WinsomeHelper;
import it.winsome.common.entity.Comment;
import it.winsome.common.entity.Post;
import it.winsome.common.entity.User;
import it.winsome.common.entity.Vote;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.network.NetMessage;
import it.winsome.common.network.enums.NetMessageType;
import it.winsome.server.ServerLogic;
import it.winsome.server.ServerMain;

import java.net.*;
import java.util.*;

public class RecalculateWallet implements Runnable {
    private final ServerLogic serverLogic;
    private final DatagramSocket multicastSocket;
    private final InetAddress multicastAddress;
    private final int multicastPort;
    private NetMessage writableMessage;
    private final double percentageAuthor;

    public RecalculateWallet(String multicastIp, int multicastPort, double percentageAuthor) throws SocketException, UnknownHostException {
        serverLogic = ServerMain.getServerLogic();
        multicastSocket = new DatagramSocket();
        multicastAddress = InetAddress.getByName(multicastIp);
        this.multicastPort = multicastPort;
        this.percentageAuthor = percentageAuthor;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        HashMap<String, List<Double>> rewards = new HashMap<>();
        // calculate rewards
        Collection<Post> posts = serverLogic.getPostsResource();
        for(Post post : posts) {
            calculateIterationAmount(post, rewards);
        }
        serverLogic.unlockPosts();

        //update users wallet
        boolean didUpdate = rewards.size() > 0;
        if(didUpdate) {
            Map<String, User> users = serverLogic.getUsersResource();
            for(Map.Entry<String, List<Double>> entry : rewards.entrySet()) {
                User user = users.get(entry.getKey());
                if(user == null) continue;

                user.prepareRead();
                for(Double transactionValue : entry.getValue()) {
                    SynchronizedObject.prepareInWriteMode(user.getWallet());
                    user.getWallet().addTransaction(transactionValue);
                    user.getWallet().releaseWrite();
                }
                user.releaseRead();
            }
            serverLogic.unlockUsers();
        }

        // recalculate
        long elapsed = System.currentTimeMillis() - start;
        WinsomeHelper.printfDebug("Recalculated wallets %dms!", elapsed);
        if(!didUpdate) return;
        WinsomeHelper.printlnDebug("Sending wallet update!");
        writableMessage = NetMessage.reuseWritableNetMessageOrCreate(writableMessage, NetMessageType.NotifyWallet, 0);
        sendMulticastMessage();
    }

    private void calculateIterationAmount(Post post, Map<String, List<Double>> interactingUsers) {
        SynchronizedObject.prepareInWriteMode(post);
        int it = post.getCurrentIteration() + 1;

        Set<String> contributors = new HashSet<>();
        double votesScore = 0;

        for(Vote vote : post.getVotes()) {
            SynchronizedObject.prepareInWriteMode(vote);
            if(!vote.isNeedIteration()) {
                vote.releaseWrite();
                continue;
            }
            vote.setNeedIteration(true);
            contributors.add(vote.getFrom());
            votesScore += vote.getType() == VoteType.UP ? 1 : -1;
            vote.releaseWrite();
        }
        votesScore = Math.min(votesScore, 0);

        double commentsScore = 0;
        Map<String, Integer> commentsCount = new HashMap<>();
        for(Comment comment : post.getComments()) {
            comment.prepareRead();
            if(commentsCount.containsKey(comment.getOwner())) {
                commentsCount.replace(comment.getOwner(), commentsCount.get(comment.getOwner()) + 1);
            } else {
                commentsCount.put(comment.getOwner(), 1);
            }
            comment.releaseRead();
        }

        for(Comment comment : post.getComments()) {
            SynchronizedObject.prepareInWriteMode(comment);
            if(!comment.isNeedIteration()) {
                comment.releaseWrite();
                continue;
            }

            comment.setNeedIteration(false);
            contributors.add(comment.getOwner());
            commentsScore += (2/
                    (1 + Math.pow(
                            Math.E, -(commentsCount.get(comment.getOwner()) - 1)
            )));
            comment.releaseWrite();
        }

        double total = (Math.log(votesScore + 1) + Math.log(commentsScore + 1)) / it;
        if(contributors.size() == 0) {
            post.releaseWrite();
            return;
        }
        double authorAmount = total * percentageAuthor / 100;
        double othersAmount = (total - authorAmount) / (double)contributors.size();
        if(interactingUsers.containsKey(post.getUsername())) {
            interactingUsers.get(post.getUsername()).add(authorAmount);
        } else {
            interactingUsers.put(post.getUsername(), new ArrayList<Double>() {{ add(authorAmount); }});
        }

        post.setCurrentIteration(it);
        post.releaseWrite();

        for(String contributor : contributors) {
            if(interactingUsers.containsKey(contributor)) {
                interactingUsers.get(contributor).add(othersAmount);
            } else {
                interactingUsers.put(contributor, new ArrayList<Double>() {{ add(othersAmount); }});
            }
        }
    }

    private void sendMulticastMessage() {
        if(!writableMessage.sendMessage(multicastSocket, multicastAddress, multicastPort)) {
            WinsomeHelper.printlnDebug("Error while sending multicast message!");
        }
    }

    public void shutdownMulticast() {
        multicastSocket.close();
    }
}
