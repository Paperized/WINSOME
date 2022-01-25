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

    protected void initSynchronizedObject() throws SynchronizedInitException {
        if(isInitialized) throw new SynchronizedInitException();
        rwLock = new ReentrantReadWriteLock();
        readHolders = new HashSet<>();
        synchronizationEnabled = false;
        isInitialized = true;
    }

    protected void checkReadSynchronization() throws SynchronizationException {
        if(synchronizationEnabled) {
            if(!isThreadReading() && !isThreadWriting())
                throw new SynchronizationException();
        }
    }

    protected void checkWriteSynchronization() throws SynchronizationException {
        if(synchronizationEnabled) {
            if(!isThreadWriting())
                throw new SynchronizationException();
        }
    }

    public void prepareRead() {
        if(!synchronizationEnabled || isThreadReading()) return;
        rwLock.readLock().lock();
        readHolders.add(Thread.currentThread());
    }

    public void prepareWrite() throws DeadlockPreventionException {
        if(!synchronizationEnabled || isThreadWriting()) return;
        if(isThreadReading()) throw new DeadlockPreventionException();
        rwLock.writeLock().lock();
    }

    public boolean downgradeToRead() {
        if(!synchronizationEnabled || !isThreadWriting()) return false;
        prepareRead();
        releaseWrite();
        return true;
    }

    public void releaseRead() {
        if(!synchronizationEnabled || !isThreadReading()) return;
        rwLock.readLock().unlock();
        readHolders.remove(Thread.currentThread());
    }

    public void releaseWrite() {
        if(!synchronizationEnabled || !isThreadWriting()) return;
        rwLock.writeLock().unlock();
    }

    public boolean isThreadReading() {
        return readHolders.contains(Thread.currentThread());
    }

    public boolean isThreadWriting() {
        return rwLock.isWriteLockedByCurrentThread();
    }

    public <T extends SynchronizedObject> T enableSynchronization(boolean recursive) {
        synchronizationEnabled = true;
        return (T) this;
    }

    public <T extends SynchronizedObject> T disableSynchronization() {
        synchronizationEnabled = false;
        return (T) this;
    }

    public boolean isSynchronizationEnabled() {
        return synchronizationEnabled;
    }

    protected Object cloneAndResetSynchronizer() throws CloneNotSupportedException {
        SynchronizedObject so = (SynchronizedObject) super.clone();
        so.isInitialized = false;
        try {
            so.initSynchronizedObject();
        } catch (SynchronizedInitException e) { return null; }
        return so;
    }

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
