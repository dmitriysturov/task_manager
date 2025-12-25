package com.example.task_manager.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "task_tags",
        primaryKeys = {"taskId", "tagId"},
        foreignKeys = {
                @ForeignKey(entity = TaskEntity.class, parentColumns = "id", childColumns = "taskId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TagEntity.class, parentColumns = "id", childColumns = "tagId", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("taskId"), @Index("tagId")})
public class TaskTagCrossRef {

    public long taskId;
    public long tagId;

    public TaskTagCrossRef(long taskId, long tagId) {
        this.taskId = taskId;
        this.tagId = tagId;
    }
}