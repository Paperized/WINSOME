package it.winsome.common;

import it.winsome.common.entity.abstracts.BaseSocialEntity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provide common functions
 */
public class WinsomeHelper {
    private static boolean debugMode = false;
    public static boolean isDebugMode() { return debugMode; }
    public static void setDebugMode(boolean debugMode) { WinsomeHelper.debugMode = debugMode; }

    /**
     * Print a string if in debug mode
     * @param str string
     */
    public static void printDebug(String str) {
        if(debugMode) {
            System.out.print(str);
        }
    }

    /**
     * Print a formatted string if in debug mode
     * @param format formatted string
     * @param args arguments
     */
    public static void printfDebug(String format, Object... args) {
        if(debugMode) {
            System.out.printf(String.format("< %s\n", format), args);
        }
    }

    /**
     * PrintLn a string if in debug mode
     * @param str string to print
     */
    public static void printlnDebug(String str) {
        if(debugMode) {
            System.out.println(str);
        }
    }

    /**
     * Unify an iterator in a formatted string similar to Arrays.toString()
     * @param it iterator
     * @param <T> type
     * @return formatted string
     */
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

    /**
     * Acquire a read lock
     * @param rwLock rwlock
     * @return lock
     */
    public static Lock acquireReadLock(ReadWriteLock rwLock) {
        Lock rLock = rwLock.readLock();
        rLock.lock();
        return rLock;
    }

    /**
     * Acquire a write lock
     * @param rwLock rwlock
     * @return lock
     */
    public static Lock acquireWriteLock(ReadWriteLock rwLock) {
        Lock wLock = rwLock.writeLock();
        wLock.lock();
        return wLock;
    }

    /**
     * Release all locks
     * @param locks locks
     */
    public static void releaseAllLocks(Lock... locks) {
        for(Lock lock : locks) {
            lock.unlock();
        }
    }

    /**
     * Acquire the read lock for all synchronized objects
     * @param col collection of synchronized objects
     */
    public static void prepareReadCollection(Collection<? extends SynchronizedObject> col) {
        for (SynchronizedObject x : col) {
            x.prepareRead();
        }
    }

    /**
     * Release the read lock for all synchronized objects
     * @param col collection of synchronized objects
     */
    public static void releaseReadCollection(Collection<? extends SynchronizedObject> col) {
        for (SynchronizedObject x : col) {
            x.releaseRead();
        }
    }

    /**
     * Deep copy each element inside a Stream into a new List
     * @param stream stream of objects
     * @param <T> type
     * @return new list
     */
    public static <T extends BaseSocialEntity> List<T> deepCopySynchronizedList(Stream<T> stream) {
        return stream.map(x -> {
            x.prepareRead();
            T copied = x.deepCopyAs();
            x.releaseRead();
            return copied;
        }).collect(Collectors.toList());
    }

    /**
     * Deep copy each element inside a Stream into a new Map
     * @param stream stream of objects
     * @param <T> type
     * @return new map
     */
    public static <T extends BaseSocialEntity, K, V> Map<K, V> deepCopySynchronizedMap(Stream<T> stream, Function<T, K> keyMap, Function<T, V> keyValue) {
        return stream.map(x -> {
            x.prepareRead();
            T copied = x.deepCopyAs();
            x.releaseRead();
            return copied;
        }).collect(Collectors.toMap(keyMap, keyValue));
    }

    /**
     * Trim the string and lower case
     * @param username input username
     * @return output username
     */
    public static String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }

    /**
     * Trim the string and lower case
     * @param tag input tag
     * @return output tag
     */
    public static String normalizeTag(String tag) {
        return tag.trim().toLowerCase();
    }

    /**
     *	Metodo per convertire un array di byte in una stringa esadecimale
     * 	@param s la stringa di input
     *	@return una stringa esadecimale leggibile
     */
    public static String generateFromSHA256(String s) {
        byte[] hash;
        try {
            hash = sha256(s);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
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
