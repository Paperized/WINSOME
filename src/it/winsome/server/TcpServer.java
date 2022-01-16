package it.winsome.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.*;

public class TcpServer {
    private InetSocketAddress address;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private boolean isClosing;
    private final long keepAliveThreadPoolTimerMinutes;
    private final long timeoutTerminationThreadPoolMs;

    private ExecutorService requestHandler;

    public TcpServer(long keepAliveThreadPoolTimerMinutes, long timeoutTerminationThreadPoolMs) {
        this.keepAliveThreadPoolTimerMinutes = keepAliveThreadPoolTimerMinutes;
        this.timeoutTerminationThreadPoolMs = timeoutTerminationThreadPoolMs;
    }

    public void initServer(String ip, int port) throws IOException {
        address = new InetSocketAddress(ip, port);
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(address);
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, null);

        requestHandler = new ThreadPoolExecutor(2, Runtime.getRuntime().availableProcessors(),
                keepAliveThreadPoolTimerMinutes, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
    }

    public void stopServer() throws IOException {
        requestHandler.shutdown();
        try {
            if(!requestHandler.awaitTermination(timeoutTerminationThreadPoolMs, TimeUnit.MILLISECONDS)) {
                requestHandler.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        isClosing = true;
        selector.close();
        serverSocketChannel.close();

        System.out.println("Server TCP closed!");
    }

    public void startServer() throws IOException {
        System.out.println("Server TCP started with port " + address.getPort() + "!");
        while(true) {
            int numKeys = selector.select();
            if(isClosing)
                return;
            if(numKeys == 0)
                continue;

            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                if(!key.isValid()) {
                    continue;
                }

                if(key.isAcceptable()) {
                    handleAccept(key);
                } else if(key.isReadable()) {
                    requestHandler.execute(new RequestHandler(this, key));
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        System.out.println("Accepted connection from " + client);
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
    }

    public void onHandlerFinish() {
        selector.wakeup();
    }
}
