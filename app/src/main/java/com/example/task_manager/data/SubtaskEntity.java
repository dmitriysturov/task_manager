package com.example.task_manager.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "subtasks",
        foreignKeys = @ForeignKey(
                entity = TaskEntity.class,
                parentColumns = "id",
                childColumns = "taskId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("taskId")}
)
public class SubtaskEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long taskId;

    @NonNull
    public String title;

    public boolean done;

    public long createdAt;

    public long updatedAt;

    public SubtaskEntity() {
        this.title = "";
    }

    public SubtaskEntity(long taskId, @NonNull String title, boolean done, long createdAt, long updatedAt) {
        this.taskId = taskId;
        this.title = title;
        this.done = done;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}