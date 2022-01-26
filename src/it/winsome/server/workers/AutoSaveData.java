package it.winsome.server.workers;

import it.winsome.common.WinsomeHelper;
import it.winsome.server.ServerLogic;

/**
 * Automatically save all entities of the social network
 */
public class AutoSaveData implements Runnable {
    private final ServerLogic serverLogic;

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
