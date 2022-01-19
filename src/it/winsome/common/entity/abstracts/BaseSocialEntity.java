package it.winsome.common.entity.abstracts;

import it.winsome.common.entity.Post;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

public abstract class BaseSocialEntity implements Serializable, Cloneable {
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
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Timestamp getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Timestamp creationDate) {
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

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public <T extends BaseSocialEntity> T deepCopyAs() {
        try {
            Object cloned = super.clone();
            return (T) cloned;
        } catch (CloneNotSupportedException | ClassCastException e) {
            e.printStackTrace();
            return null;
        }
    }
}
