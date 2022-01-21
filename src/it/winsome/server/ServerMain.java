package it.winsome.server;

import it.winsome.server.config.ServerConfiguration;
import it.winsome.common.WinsomeHelper;
import it.winsome.server.workers.AutoSaveData;
import it.winsome.server.workers.RecalculateWallet;

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
    private static ServerConnector tcpServer;
    private static ScheduledExecutorService threadScheduler;

    private final static ServerConfiguration serverConfiguration = new ServerConfiguration();
    private static boolean configLoadingFailed = false;
    private static RecalculateWallet walletCalculator;

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

        tcpServer = new ServerConnector(serverConfiguration.keepAliveThreadPoolMinutes,
                    serverConfiguration.timeoutTerminationThreadPoolMs);
        tcpServer.initServer(serverConfiguration.tcpAddress, serverConfiguration.tcpPort);

        AutoSaveData dataSaver = new AutoSaveData(registerService);
        walletCalculator = new RecalculateWallet("237.0.10.10", 10909, 80);

        threadScheduler = Executors.newScheduledThreadPool(2);
        threadScheduler.scheduleAtFixedRate(walletCalculator, 10,10, TimeUnit.SECONDS);
        threadScheduler.scheduleAtFixedRate(dataSaver, serverConfiguration.autoSavePeriodSeconds,
                serverConfiguration.autoSavePeriodSeconds, TimeUnit.SECONDS);
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

            threadScheduler.shutdown();
            if(!threadScheduler.awaitTermination(serverConfiguration.timeoutOnStopAutoSaveSeconds, TimeUnit.SECONDS)) {
                threadScheduler.shutdownNow();
            }
            registerService.saveToDisk();
            walletCalculator.shutdownMulticast();

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
