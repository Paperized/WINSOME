package it.winsome.common;

import it.winsome.common.exception.DeadlockPreventionException;
import it.winsome.common.exception.SynchronizationException;
import it.winsome.common.exception.SynchronizedInitException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This represents a synchronized object, it offers basic read checks and write checks to the classes
 * that extends from this, useful for multithreading safety over the single objects
 */
public abstract class SynchronizedObject implements Cloneable {
    private transient ReentrantReadWriteLock rwLock;
    private transient Set<Thread> readHolders;
    private transient boolean synchronizationEnabled;
    private transient boolean isInitialized;

    public SynchronizedObject() {
        try {
            initSynchronizedObject();
        } catch(SynchronizedInitException e) { }
    }

    /**
     * Initialize the synchronized object, by default the synchronization is disabled but can be enabled after the init
     * @throws SynchronizedInitException if the object is already initialized
     */
    protected void initSynchronizedObject() throws SynchronizedInitException {
        if(isInitialized) throw new SynchronizedInitException();
        rwLock = new ReentrantReadWriteLock();
        readHolders = new HashSet<>();
        synchronizationEnabled = false;
        isInitialized = true;
    }

    /**
     * Throw an exception if the synchronization is enabled and the thread does not own a read lock (or write)
     * @throws SynchronizationException if no read lock is owned
     */
    protected void checkReadSynchronization() throws SynchronizationException {
        if(synchronizationEnabled) {
            if(isThreadWriting() || isThreadReading()) {
                return;
            }

            throw new SynchronizationException();
        }
    }

    /**
     * Throw an exception if the synchronization is enabled and the thread does not own the write lock
     * @throws SynchronizationException if no write lock is owned
     */
    protected void checkWriteSynchronization() throws SynchronizationException {
        if(synchronizationEnabled) {
            if(!isThreadWriting())
                throw new SynchronizationException();
        }
    }

    /**
     * Acquire the read lock
     */
    public void prepareRead() {
        if(!synchronizationEnabled || isThreadReading()) return;
        rwLock.readLock().lock();
        readHolders.add(Thread.currentThread());
    }

    /**
     * Acquire the write lock, upgrading from read to write is not permitted and will result in an exception
     * @throws DeadlockPreventionException if the caller has a read lock
     */
    public void prepareWrite() throws DeadlockPreventionException {
        if(!synchronizationEnabled || isThreadWriting()) return;
        if(isThreadReading()) throw new DeadlockPreventionException();
        rwLock.writeLock().lock();
    }

    /**
     * Downgrade the caller from having a write lock to a read lock
     * @return if it was downgraded
     */
    public boolean downgradeToRead() {
        if(!synchronizationEnabled || !isThreadWriting()) return false;
        prepareRead();
        releaseWrite();
        return true;
    }

    /**
     * Release a read lock
     */
    public void releaseRead() {
        if(!synchronizationEnabled || !isThreadReading()) return;
        rwLock.readLock().unlock();
        readHolders.remove(Thread.currentThread());
    }

    /**
     * Release a write lock
     */
    public void releaseWrite() {
        if(!synchronizationEnabled || !isThreadWriting()) return;
        rwLock.writeLock().unlock();
    }

    /**
     * Check if this thread has a read lock
     * @return if the current thread is reading
     */
    public boolean isThreadReading() {
        return readHolders.contains(Thread.currentThread());
    }

    /**
     * Check if this thread has a write lock
     * @return true if the current thread is writing
     */
    public boolean isThreadWriting() {
        return rwLock.isWriteLockedByCurrentThread();
    }

    /**
     * Enable the synchronization for this object, can be used recursively in case of complex objects
     * @param recursive is the synchronization recursive
     * @param <T> conversion type
     * @return this entity
     */
    public <T extends SynchronizedObject> T enableSynchronization(boolean recursive) {
        synchronizationEnabled = true;
        return (T) this;
    }

    /**
     * Disable the synchronization for this entity
     * @param <T> conversion type
     * @return this entity
     */
    public <T extends SynchronizedObject> T disableSynchronization() {
        synchronizationEnabled = false;
        return (T) this;
    }

    /**
     * Check if the synchronization is enabled
     * @return if this object is synchronized
     */
    public boolean isSynchronizationEnabled() {
        return synchronizationEnabled;
    }

    /**
     * Clone this entity and initialize the synchronization object
     * @return a clone of this entity
     * @throws CloneNotSupportedException if clone is not supported
     */
    protected Object cloneAndResetSynchronizer() throws CloneNotSupportedException {
        SynchronizedObject so = (SynchronizedObject) super.clone();
        so.isInitialized = false;
        try {
            so.initSynchronizedObject();
        } catch (SynchronizedInitException e) { return null; }
        return so;
    }

    /**
     * Utility function acquire the write lock without handling exceptions
     * @param so entity
     * @return true if the lock was acquired
     */
    public static boolean prepareInWriteMode(SynchronizedObject so) {
        try {
            so.prepareWrite();
        } catch(DeadlockPreventionException e) {
            so.releaseRead();
            try {
                // should never fail since we released the problematic lock
                so.prepareWrite();
            } catch (DeadlockPreventionException ex) { return false; }
        }

        return true;
    }
}
