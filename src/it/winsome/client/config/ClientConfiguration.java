package it.winsome.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ClientConfiguration {
    public String rmiServiceName = "serviceName";
    public int rmiServicePort = 6000;
    public String serverTcpAddress = "127.0.0.1";
    public int serverTcpPort = 5959;

    public void loadFromJson(String path) throws IOException {
        Gson gson = new GsonBuilder().create();
        String jsonPost = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        ClientConfiguration config = gson.fromJson(jsonPost, ClientConfiguration.class);
        rmiServiceName = config.rmiServiceName;
        rmiServicePort = config.rmiServicePort;
        serverTcpAddress = config.serverTcpAddress;
        serverTcpPort = config.serverTcpPort;
    }

    public static boolean generateDefaultFile(String path) {
        Gson gsonPost = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        String json = gsonPost.toJson(new ClientConfiguration());
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
