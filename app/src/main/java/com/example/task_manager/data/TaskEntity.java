package com.example.task_manager.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class TaskEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String title;

    private boolean done;

    public long createdAt;

    @Nullable
    public Long dueAt;

    @NonNull
    public String description = "";

    public TaskEntity() {
    }

    @Ignore
    public TaskEntity(@NonNull String title, boolean done, long createdAt, @Nullable Long dueAt) {
        this(title, done, createdAt, dueAt, "");
    }

    @Ignore
    public TaskEntity(@NonNull String title, boolean done, long createdAt, @Nullable Long dueAt, @NonNull String description) {
        this.title = title;
        this.done = done;
        this.createdAt = createdAt;
        this.dueAt = dueAt;
        this.description = description;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public Long getDueAt() {
        return dueAt;
    }

    public void setDueAt(@Nullable Long dueAt) {
        this.dueAt = dueAt;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    public void setDescription(@NonNull String description) {
        this.description = description;
    }
}