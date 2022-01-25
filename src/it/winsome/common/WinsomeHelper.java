package it.winsome.common;

import it.winsome.common.entity.abstracts.BaseSocialEntity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static Lock acquireReadLock(ReadWriteLock rwLock) {
        Lock rLock = rwLock.readLock();
        rLock.lock();
        return rLock;
    }

    public static Lock acquireWriteLock(ReadWriteLock rwLock) {
        Lock wLock = rwLock.writeLock();
        wLock.lock();
        return wLock;
    }

    public static void releaseAllLocks(Lock... locks) {
        for(Lock lock : locks) {
            lock.unlock();
        }
    }

    public static void prepareReadCollection(Collection<? extends SynchronizedObject> col) {
        for (SynchronizedObject x : col) {
            x.prepareRead();
        }
    }

    public static void releaseReadCollection(Collection<? extends SynchronizedObject> col) {
        for (SynchronizedObject x : col) {
            x.releaseRead();
        }
    }

    public static <T extends BaseSocialEntity> List<T> deepCopySynchronizedList(Stream<T> stream) {
        return stream.map(x -> {
            x.prepareRead();
            T copied = x.deepCopyAs();
            x.releaseRead();
            return copied;
        }).collect(Collectors.toList());
    }

    public static <T extends BaseSocialEntity> Set<T> deepCopySynchronizedSet(Stream<T> stream) {
        return stream.map(x -> {
            x.prepareRead();
            T copied = x.deepCopyAs();
            x.releaseRead();
            return copied;
        }).collect(Collectors.toSet());
    }

    public static <T extends BaseSocialEntity, K, V> Map<K, V> deepCopySynchronizedMap(Stream<T> stream, Function<T, K> keyMap, Function<T, V> keyValue) {
        return stream.map(x -> {
            x.prepareRead();
            T copied = x.deepCopyAs();
            x.releaseRead();
            return copied;
        }).collect(Collectors.toMap(keyMap, keyValue));
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
