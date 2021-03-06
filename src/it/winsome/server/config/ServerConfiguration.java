package it.winsome.server.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Contains all server configuration fields, can read from files and generate a default template
 */
public class ServerConfiguration {
    public String rmiServiceName = "serviceName";
    public int rmiServicePort = 6000;
    public String tcpAddress = "127.0.0.1";
    public int tcpPort = 5959;
    public String multicastIp = "237.0.10.10";
    public int multicastPort = 10909;
    public double authorPercentage = 80D;
    public String dataFolder = "data/";
    public long autoSavePeriodSeconds = 20L;
    public long walletCalculatorPeriodSeconds = 20L;
    public long timeoutOnStopAutoSaveSeconds = 3L;
    public long keepAliveThreadPoolMinutes = 5L;
    public long timeoutTerminationThreadPoolMs = 2000L;

    /**Load a configuration from a path
     * @param path path of the json file
     * @throws IOException if the file does not exist
     */
    public void loadFromJson(String path) throws IOException {
        Gson gson = new GsonBuilder().create();
        String jsonPost = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        ServerConfiguration config = gson.fromJson(jsonPost, ServerConfiguration.class);
        rmiServiceName = config.rmiServiceName;
        rmiServicePort = config.rmiServicePort;
        tcpAddress = config.tcpAddress;
        tcpPort = config.tcpPort;
        dataFolder = config.dataFolder;
        autoSavePeriodSeconds = config.autoSavePeriodSeconds;
        walletCalculatorPeriodSeconds = config.walletCalculatorPeriodSeconds;
        timeoutOnStopAutoSaveSeconds = config.timeoutOnStopAutoSaveSeconds;
        keepAliveThreadPoolMinutes = config.keepAliveThreadPoolMinutes;
        timeoutTerminationThreadPoolMs = config.timeoutTerminationThreadPoolMs;
        multicastIp = config.multicastIp;
        multicastPort = config.multicastPort;
        authorPercentage = config.authorPercentage;
    }

    /**Generated a template configuration located at path
     * @param path path of a file
     * @return true if the default configuration was generated
     */
    public static boolean generateDefaultFile(String path) {
        Gson gsonPost = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        String json = gsonPost.toJson(new ServerConfiguration());
        File file = new File(path);

        try {
            if(file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            } else {
                new File(file.getParent()).mkdirs();
            }

            file.createNewFile();
            try (FileOutputStream oFile = new FileOutputStream(file, false)) {
                oFile.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        } catch(IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
