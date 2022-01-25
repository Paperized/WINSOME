package it.winsome.common.entity.abstracts;

import it.winsome.common.SynchronizedObject;
import it.winsome.common.exception.SynchronizedInitException;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * Base class for every entity which require an id to exist
 * This class is synchronizable since it extends SynchonizedObject but can also be used without
 * it by disabling the option.
 */
public abstract class BaseSocialEntity extends SynchronizedObject implements Serializable {
    private int id;
    private Timestamp creationDate;

    public BaseSocialEntity() {
        creationDate = Timestamp.from(Instant.now());
    }

    public BaseSocialEntity(int id) {
        this();
        this.id = id;
    }

    public int getId() {
        checkReadSynchronization();
        return id;
    }

    public void setId(int id) {
        checkWriteSynchronization();
        this.id = id;
    }

    public Timestamp getCreationDate() {
        checkReadSynchronization();
        return creationDate;
    }

    public void setCreationDate(Timestamp creationDate) {
        checkWriteSynchronization();
        this.creationDate = creationDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseSocialEntity that = (BaseSocialEntity) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Create a deep copy of this entity
     * @param <T> conversion
     * @return an identical entity of this one
     */
    public <T extends BaseSocialEntity> T deepCopyAs() {
        checkReadSynchronization();
        try {
            BaseSocialEntity cloned = (BaseSocialEntity) cloneAndResetSynchronizer();
            return (T) cloned;
        } catch (CloneNotSupportedException | ClassCastException e) {
            e.printStackTrace();
            return null;
        }
    }
}
