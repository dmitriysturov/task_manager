package com.example.task_manager.data;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;

public class TaskWithGroup {

    @Embedded
    public TaskEntity task;

    @ColumnInfo(name = "groupName")
    public String groupName;

    @Nullable
    @ColumnInfo(name = "groupColor")
    public Integer groupColor;
}