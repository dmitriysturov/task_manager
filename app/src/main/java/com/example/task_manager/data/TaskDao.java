package com.example.task_manager.data;

import androidx.annotation.Nullable;
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

    @Transaction
    @Query("SELECT * FROM tasks WHERE ((:groupId IS NULL AND groupId IS NULL) OR groupId = :groupId) ORDER BY done ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithSubtasks>> observeAllWithSubtasksByGroup(@Nullable Long groupId);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    LiveData<TaskEntity> observeById(long id);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    TaskEntity getByIdSync(long id);

    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL AND dueAt BETWEEN :from AND :to ORDER BY dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> observeUndoneInRange(long from, long to);

    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY done ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> observeUndoneAll();

    @Query("SELECT * FROM tasks WHERE done = 0 AND groupId IS NULL AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> searchUndoneInInbox(String q);

    @Query("SELECT * FROM tasks WHERE done = 0 AND groupId = :groupId AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> searchUndoneInGroup(long groupId, String q);

    @Query("SELECT * FROM tasks WHERE done = 0 AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> searchUndoneAll(String q);

    @Transaction
    @Query("SELECT * FROM tasks WHERE done = 0 AND groupId IS NULL AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithSubtasks>> searchUndoneWithSubtasksInInbox(String q);

    @Transaction
    @Query("SELECT * FROM tasks WHERE done = 0 AND groupId = :groupId AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithSubtasks>> searchUndoneWithSubtasksInGroup(long groupId, String q);

    @Transaction
    @Query("SELECT * FROM tasks WHERE done = 0 AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithSubtasks>> searchUndoneWithSubtasksAll(String q);

    @Query("SELECT * FROM tasks WHERE done = 0 AND ((:groupId IS NULL AND groupId IS NULL) OR groupId = :groupId) ORDER BY done ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> observeUndoneByGroup(@Nullable Long groupId);

    @Query("SELECT * FROM tasks WHERE done = 1 AND dueAt IS NOT NULL AND dueAt BETWEEN :from AND :to ORDER BY dueAt DESC, createdAt DESC")
    LiveData<List<TaskEntity>> observeDoneInRange(long from, long to);

    @Query("SELECT * FROM tasks WHERE done = 1 ORDER BY COALESCE(dueAt, createdAt) DESC")
    LiveData<List<TaskEntity>> observeDoneAll();

    @Insert
    long insert(TaskEntity task);

    @Update
    void update(TaskEntity task);

    @Delete
    void delete(TaskEntity task);

    @Query("UPDATE tasks SET groupId = NULL WHERE groupId = :groupId")
    void clearGroupId(long groupId);
}