package it.winsome.server.workers;

import it.winsome.common.WinsomeHelper;
import it.winsome.server.UserServiceImpl;

public class AutoSaveData implements Runnable {
    UserServiceImpl userService;

    public AutoSaveData(UserServiceImpl userService) {
        this.userService = userService;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        userService.saveToDisk();
        long elapsed = System.currentTimeMillis() - start;
        WinsomeHelper.printfDebug("Autosave completed in %dms!", elapsed);
    }
}
