package it.winsome.client;

import it.winsome.common.Pair;
import it.winsome.common.WinsomeHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

public class ClientMain {
    private static final BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) throws Exception {
        WinsomeHelper.setDebugMode(true);
        ClientApplication app = new ClientApplication();
        try {
            app.loadConfiguration("client_config.json");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            app.initApplication();
        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
            return;
        }

        while(true) {
            if(app.consumeWalletNotification()) {
                ClientApplication.printResponse("Wallet notification: your amount might be updated!");
            }
            String command = reader.readLine().trim();
            if(command.equals("quit"))
                break;

            String commandName = getCommand(command);
            if(commandName.equals("Unknown")) {
                ClientApplication.printError("This command does not exists!");
                continue;
            }

            String[] arguments;
            try {
                arguments = getCommandArgs(command, commandName);
                app.sendCommand(commandName, arguments);
            } catch(InvalidParameterException e) {
                ClientApplication.printError(e.getMessage());
            }
        }

        app.close();
    }

    /**
     * Get the command arguments from a string and it's relative command
     * @param line command arguments string
     * @param cmd command related
     * @return an array of tokenized arguments
     * @throws InvalidParameterException if the command arguments does not meet the requirements
     */
    private static String[] getCommandArgs(String line, String cmd) throws InvalidParameterException {
        String lineArg = excludeCommandName(line, cmd);
        List<String> args = new ArrayList<>();
        switch(cmd) {
            case "logout":
            case "list users":
            case "list followers":
            case "list following":
                ensureNParameters(args, 0, lineArg, false, 0);
                break;
            case "register":
                ensureNParameters(args, 3, lineArg, true, 7);
                break;
            case "follow":
            case "unfollow":
            case "show post":
            case "delete":
            case "rewin":
                ensureNParameters(args, 1, lineArg, false, 0);
                break;
            case "wallet":
            case "show feed":
            case "blog":
                ensureNParameters(args, 0, lineArg, true, 1);
                break;
            case "login":
            case "post":
            case "rate":
            case "comment":
                ensureNParameters(args, 2, lineArg, false, 0);
                break;
            case "help":
                ensureNParameters(args, 0, lineArg, true, 10);
                break;
        }

        return args.toArray(new String[0]);
    }

    /**
     * Ensures that an argument string contains an expected amount of arguments and maybe a couple more
     * then return the list of arguments
     * @param args list of arguments in output
     * @param expected expected number of arguments obligatory
     * @param lineArgs argument string
     * @param canExceed if the string can have optional arguments
     * @param maxExceed max number of arguments including all type of parameters (maxExceed >= expected)
     * @throws InvalidParameterException if a parameter is invalid
     */
    private static void ensureNParameters(List<String> args, int expected, String lineArgs, boolean canExceed, int maxExceed) throws InvalidParameterException {
        Pair<String, String> step;
        int i = 0;
        while(i < expected || (canExceed && i < maxExceed)) {
            if(lineArgs.equals("")) {
                if(!canExceed)
                    throw new InvalidParameterException("This command expect " + expected + " arguments but only " + i + " are provided!");
                else if(i < expected)
                    throw new InvalidParameterException("This command expect " + expected + " arguments but only " + i + " are provided!");
                else
                    break;
            }

            step = readStringParameter(lineArgs);
            args.add(step.first);
            lineArgs = step.second;
            i++;
        }

        if(!canExceed && !lineArgs.equals("")) {
            throw new InvalidParameterException("This command expect " + expected + " arguments but more are provided!");
        }
        if(canExceed && i >= maxExceed && !lineArgs.equals("")) {
            throw new InvalidParameterException("This command maximum " + maxExceed + " arguments but more are provided!");
        }
     }

    /**
     * Read the first argument from a string
     * @param lineArgs argument string
     * @return a pair containing the tokenized argument and the modified argument string without the previous parameter
     * @throws InvalidParameterException if the argument is invalid
     */
    private static Pair<String, String> readStringParameter(String lineArgs) throws InvalidParameterException {
        if(lineArgs.charAt(0) == '"') {
            int index = lineArgs.indexOf('"', 1);
            if(index == -1)
                throw new InvalidParameterException("A string parameter starting with \" does not terminate!");
            String arg = lineArgs.substring(1, index);
            return new Pair<>(arg, lineArgs.substring(index + 1).trim());
        } else {
            int index = lineArgs.indexOf(' ', 1);
            if(index == -1)
                index = lineArgs.length();
            String arg = lineArgs.substring(0, index);
            return new Pair<>(arg, index > lineArgs.length() - 1 ? "" : lineArgs.substring(index + 1).trim());
        }
    }

    /**
     * Remove the command name from the whole input command
     * @param line input command
     * @param cmd command name to remove
     * @return command's arguments
     */
    private static String excludeCommandName(String line, String cmd) {
        return line.substring(cmd.length()).trim();
    }

    /**
     * Get the command name from an input string
     * @param line input string
     * @return the command name
     */
    private static String getCommand(String line) {
        for(String key : ClientApplication.getCommandsAvailable()) {
            if(startsWithCommand(line, key)) {
                return key;
            }
        }

        return "Unknown";
    }

    /**
     * Check if a string starts with a command name
     * @param str string
     * @param cmd command name
     * @return true if start with a command name
     */
    private static boolean startsWithCommand(String str, String cmd) {
        return str.startsWith(cmd) && isNextCharacterEmpty(str, cmd);
    }

    /**
     * Check if the next character after the match string is an empty space (if a next character exists)
     * @param str whole string
     * @param match initial match
     * @return true if there is a space or if there is not a character afterwards
     */
    private static boolean isNextCharacterEmpty(String str, String match) {
        int index = str.indexOf(match) + match.length();
        if(index == str.length()) return true;
        if(index < str.length())
            return str.charAt(index) == ' ';

        return false;
    }
}