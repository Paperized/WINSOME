package it.winsome.test;

import it.winsome.client.ClientMain;
import it.winsome.server.ServerMain;

import java.io.IOException;
import java.rmi.NotBoundException;

public class MainTest {

    public static void main(String[] args) throws IOException, NotBoundException, InterruptedException, InstantiationException, IllegalAccessException {
        new Thread(() -> {
            try {
                ServerMain.main(args);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        Thread.sleep(2000L);
        ClientMain.main(null);
    }
}
