package com.example.task_manager.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY done ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithSubtasks>> observeAllWithSubtasks();

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    LiveData<TaskEntity> observeById(long id);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    TaskEntity getByIdSync(long id);

    @Insert
    long insert(TaskEntity task);

    @Update
    void update(TaskEntity task);

    @Delete
    void delete(TaskEntity task);
}