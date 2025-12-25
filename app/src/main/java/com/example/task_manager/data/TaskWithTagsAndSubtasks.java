package com.example.task_manager.data;

import androidx.annotation.Nullable;
import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class TaskWithTagsAndSubtasks {

    @Embedded
    public TaskEntity task;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<SubtaskEntity> subtasks;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(value = TaskTagCrossRef.class, parentColumn = "taskId", entityColumn = "tagId")
    )
    public List<TagEntity> tags;

    @Nullable
    @Relation(parentColumn = "groupId", entityColumn = "id")
    public GroupEntity group;
}