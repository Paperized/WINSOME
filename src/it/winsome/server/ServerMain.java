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
    private static Registry reg;
    private static ServerConnector tcpServer;
    private static ScheduledExecutorService walletUpdater, autoSaveUpdater;

    private final static ServerConfiguration serverConfiguration = new ServerConfiguration();
    private static boolean configLoadingFailed = false;
    private static RecalculateWallet walletCalculator;
    private static ServerLogic serverLogic;

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

        serverLogic = new ServerLogic(serverConfiguration.dataFolder);

        LocateRegistry.createRegistry(serverConfiguration.rmiServicePort);
        reg = LocateRegistry.getRegistry(serverConfiguration.rmiServicePort);

        UserCallbackServerImpl registerService = new UserCallbackServerImpl(serverLogic);
        reg.rebind(serverConfiguration.rmiServiceName, registerService);
        System.out.printf(
                "Server pronto (nome servizio = %s, porta registry = %d)\n",
                serverConfiguration.rmiServiceName, serverConfiguration.rmiServicePort);

        tcpServer = new ServerConnector(serverConfiguration.keepAliveThreadPoolMinutes,
                    serverConfiguration.timeoutTerminationThreadPoolMs);
        tcpServer.initServer(serverConfiguration.tcpAddress, serverConfiguration.tcpPort);

        AutoSaveData dataSaver = new AutoSaveData(serverLogic);
        walletCalculator = new RecalculateWallet(serverConfiguration.multicastIp,
                serverConfiguration.multicastPort, serverConfiguration.authorPercentage);

        autoSaveUpdater = Executors.newScheduledThreadPool(1);
        walletUpdater = Executors.newScheduledThreadPool(1);
        walletUpdater.scheduleWithFixedDelay(walletCalculator, 0L,20L, TimeUnit.SECONDS);
        autoSaveUpdater.scheduleWithFixedDelay(dataSaver, serverConfiguration.autoSavePeriodSeconds,
                serverConfiguration.autoSavePeriodSeconds, TimeUnit.SECONDS);
        serverLogic.test();
        tcpServer.startServer();
    }

    /**
     * Get the ServerLogic object
     * @return the server logic
     */
    public static ServerLogic getServerLogic() {
        return serverLogic;
    }
    public static ServerConfiguration getServerConfiguration() { return serverConfiguration; }

    /**
     * Clean the connections and additional thread working
     */
    private static void onQuit() {
        try {
            WinsomeHelper.printlnDebug("Closing...!");
            if(configLoadingFailed)
                return;

            walletUpdater.shutdown();
            autoSaveUpdater.shutdown();
            if(!autoSaveUpdater.awaitTermination(serverConfiguration.timeoutOnStopAutoSaveSeconds, TimeUnit.SECONDS)) {
                autoSaveUpdater.shutdownNow();
            }
            if(!walletUpdater.awaitTermination(serverConfiguration.timeoutOnStopAutoSaveSeconds, TimeUnit.SECONDS)) {
                walletUpdater.shutdownNow();
            }

            serverLogic.saveToDisk();
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
