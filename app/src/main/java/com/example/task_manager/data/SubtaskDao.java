package com.example.task_manager.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SubtaskDao {

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY createdAt ASC")
    LiveData<List<SubtaskEntity>> observeByTaskId(long taskId);

    @Insert
    long insert(SubtaskEntity subtask);

    @Update
    void update(SubtaskEntity subtask);

    @Delete
    void delete(SubtaskEntity subtask);

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    void deleteByTaskId(long taskId);
}