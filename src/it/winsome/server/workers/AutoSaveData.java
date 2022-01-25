package it.winsome.server.workers;

import it.winsome.common.WinsomeHelper;
import it.winsome.server.ServerLogic;

public class AutoSaveData implements Runnable {
    ServerLogic serverLogic;

    public AutoSaveData(ServerLogic serverLogic) {
        this.serverLogic = serverLogic;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        serverLogic.saveToDisk();
        long elapsed = System.currentTimeMillis() - start;
        WinsomeHelper.printfDebug("Autosave completed in %dms!", elapsed);
    }
}
