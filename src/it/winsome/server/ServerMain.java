package it.winsome.server;

import it.winsome.server.config.ServerConfiguration;
import it.winsome.common.WinsomeHelper;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerMain {
    private static UserServiceImpl registerService;
    private static Registry reg;
    private static TcpServer tcpServer;
    private static ScheduledExecutorService autoSaveExecutor;

    private final static ServerConfiguration serverConfiguration = new ServerConfiguration();
    private static boolean configLoadingFailed = false;

    public static void main(String[] args) throws IOException {
        WinsomeHelper.setDebugMode(true);
        Runtime.getRuntime().addShutdownHook(new Thread(ServerMain::onQuit));

        try {
            serverConfiguration.loadFromJson("./server_config.json");
            WinsomeHelper.printlnDebug("Loaded server configuration successfully!");
        } catch (IOException e) {
            configLoadingFailed = true;

            if((e instanceof NoSuchFileException)) {
                WinsomeHelper.printlnDebug("Configuration not found at path ./server_config.json!");
                if(ServerConfiguration.generateDefaultFile("./server_config.json")) {
                    WinsomeHelper.printlnDebug("Generated a server configuration template!");
                }
            } else {
                e.printStackTrace();
            }
            return;
        }

        LocateRegistry.createRegistry(serverConfiguration.rmiServicePort);
        reg = LocateRegistry.getRegistry(serverConfiguration.rmiServicePort);

        registerService = new UserServiceImpl(serverConfiguration.dataFolder);
        reg.rebind(serverConfiguration.rmiServiceName, registerService);
        System.out.printf(
                "Server pronto (nome servizio = %s, porta registry = %d)\n",
                serverConfiguration.rmiServiceName, serverConfiguration.rmiServicePort);

        tcpServer = new TcpServer(serverConfiguration.keepAliveThreadPoolMinutes,
                    serverConfiguration.timeoutTerminationThreadPoolMs);
        tcpServer.initServer(serverConfiguration.tcpAddress, serverConfiguration.tcpPort);

        autoSaveExecutor = Executors.newSingleThreadScheduledExecutor();
        autoSaveExecutor.scheduleAtFixedRate(() -> {
            long start = System.currentTimeMillis();
            registerService.saveToDisk();
            long elapsed = System.currentTimeMillis() - start;
            WinsomeHelper.printfDebug("Autosave completed in %dms!", elapsed);
        }, serverConfiguration.autoSavePeriodSeconds, serverConfiguration.autoSavePeriodSeconds, TimeUnit.SECONDS);
        getUserServiceImpl().test();
        tcpServer.startServer();
    }

    public static UserServiceImpl getUserServiceImpl() {
        return registerService;
    }

    private static void onQuit() {
        try {
            WinsomeHelper.printlnDebug("Closing...!");
            if(configLoadingFailed)
                return;

            autoSaveExecutor.shutdown();
            if(!autoSaveExecutor.awaitTermination(serverConfiguration.timeoutOnStopAutoSaveSeconds, TimeUnit.SECONDS)) {
                autoSaveExecutor.shutdownNow();
            }
            registerService.saveToDisk();

            reg.unbind(serverConfiguration.rmiServiceName);
            System.out.println("RMI Service unbinded!");
        } catch (NotBoundException | RemoteException | InterruptedException e) {
            e.printStackTrace();
        }

        try {
            tcpServer.stopServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
