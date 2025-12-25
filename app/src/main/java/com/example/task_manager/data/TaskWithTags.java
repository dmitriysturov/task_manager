package com.example.task_manager.data;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class TaskWithTags {

    @Embedded
    public TaskEntity task;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(value = TaskTagCrossRef.class, parentColumn = "taskId", entityColumn = "tagId")
    )
    public List<TagEntity> tags;
}