package it.winsome.common;

import it.winsome.common.entity.abstracts.BaseSocialEntity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

public class WinsomeHelper {
    private static boolean debugMode = false;
    public static boolean isDebugMode() { return debugMode; }
    public static void setDebugMode(boolean debugMode) { WinsomeHelper.debugMode = debugMode; }

    public static void printDebug(String str) {
        if(debugMode) {
            System.out.print(str);
        }
    }

    public static void printfDebug(String format, Object... args) {
        if(debugMode) {
            System.out.printf(String.format("< %s\n", format), args);
        }
    }

    public static void printlnDebug(String str) {
        if(debugMode) {
            System.out.println(str);
        }
    }

    public static <T> String iteratorToString(Iterator<T> it) {
        if(it == null) throw new NullPointerException();
        StringBuilder builder = new StringBuilder(40);
        builder.append('[');
        while(it.hasNext()) {
            builder.append(it.next().toString());
            if(it.hasNext()) {
                builder.append(", ");
            }
        }

        return builder.append(']').toString();
    }

    public static String normalizeUsername(String username) {
        if(username.startsWith(" ") || username.endsWith(" ")) {
            return username.trim().toLowerCase();
        }

        return username.toLowerCase();
    }

    public static String normalizeTag(String tag) {
        if(tag.startsWith(" ") || tag.endsWith(" ")) {
            return tag.trim().toLowerCase();
        }

        return tag.toLowerCase();
    }

    public static <T extends BaseSocialEntity> T entityFromId(int id, Class<T> cl) {
        try {
            return cl.getConstructor(Integer.class).newInstance(id);
        } catch(Exception ex) {
            return null;
        }
    }

    /**
     *	Metodo per convertire un array di byte in una stringa esadecimale
     * 	@param s la stringa di input
     *	@return una stringa esadecimale leggibile
     */
    public static String generateFromSHA256(String s) {
        byte[] hash = null;
        try {
            hash = sha256(s);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     *	Metodo che calcola il valore hash SHA-256 di una stringa
     * 	@param s la stringa di input
     *  @return i byte corrispondenti al valore hash dell'input
     */
    private static byte[] sha256(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(s.getBytes());
        return md.digest();
    }
}
