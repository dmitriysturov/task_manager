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

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    LiveData<TaskEntity> observeById(long id);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    TaskEntity getByIdSync(long id);

    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL AND dueAt BETWEEN :from AND :to ORDER BY dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> observeUndoneInRange(long from, long to);

    @Query("SELECT t.*, COALESCE(g.name, :inboxName) AS groupName, g.color AS groupColor " +
            "FROM tasks t LEFT JOIN groups g ON g.id = t.groupId " +
            "WHERE t.done = 0 AND t.dueAt IS NOT NULL AND t.dueAt BETWEEN :from AND :to " +
            "AND (:applyGroupFilter = 0 OR ((:groupIdFilter IS NULL AND t.groupId IS NULL) OR t.groupId = :groupIdFilter)) " +
            "ORDER BY t.dueAt ASC, t.createdAt DESC")
    LiveData<List<TaskWithGroup>> observeUndoneInRangeWithGroup(long from, long to, @Nullable Long groupIdFilter, int applyGroupFilter, String inboxName);

    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY pinned DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> observeUndoneAll();

    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL ORDER BY pinned DESC, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> observeUndoneWithDeadline();

    @Query("SELECT * FROM tasks WHERE done = 0 AND groupId IS NULL AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY pinned DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> searchUndoneInInbox(String q);

    @Query("SELECT * FROM tasks WHERE done = 0 AND groupId = :groupId AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY pinned DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> searchUndoneInGroup(long groupId, String q);

    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY pinned DESC, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> searchUndoneWithDeadline(String q);

    @Query("SELECT * FROM tasks WHERE done = 0 AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') ORDER BY pinned DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> searchUndoneAll(String q);

    @Query("SELECT * FROM tasks WHERE done = 0 AND ((:groupId IS NULL AND groupId IS NULL) OR groupId = :groupId) ORDER BY pinned DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskEntity>> observeUndoneByGroup(@Nullable Long groupId);

    @Query("SELECT * FROM tasks WHERE done = 1 AND dueAt IS NOT NULL AND dueAt BETWEEN :from AND :to ORDER BY dueAt DESC, createdAt DESC")
    LiveData<List<TaskEntity>> observeDoneInRange(long from, long to);

    @Query("SELECT * FROM tasks WHERE done = 1 ORDER BY COALESCE(dueAt, createdAt) DESC")
    LiveData<List<TaskEntity>> observeDoneAll();

    @Query("SELECT t.*, COALESCE(g.name, :inboxName) AS groupName, g.color AS groupColor " +
            "FROM tasks t LEFT JOIN groups g ON g.id = t.groupId " +
            "WHERE t.done = 1 " +
            "ORDER BY COALESCE(t.dueAt, t.createdAt) DESC")
    LiveData<List<TaskWithGroup>> observeDoneAllWithGroup(String inboxName);
    @Insert
    long insert(TaskEntity task);

    @Update
    void update(TaskEntity task);

    @Delete
    void delete(TaskEntity task);

    @Query("UPDATE tasks SET groupId = NULL WHERE groupId = :groupId")
    void clearGroupId(long groupId);

    @Transaction
    @Query("SELECT * FROM tasks WHERE ((:groupId IS NULL AND groupId IS NULL) OR groupId = :groupId) AND (:applyTags = 0 OR id IN (SELECT taskId FROM task_tags WHERE tagId IN (:tagIds))) ORDER BY pinned DESC, done ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithTagsAndSubtasks>> observeAllWithTagsAndSubtasksByGroup(@Nullable Long groupId, int applyTags, List<Long> tagIds);

    @Transaction
    @Query("SELECT * FROM tasks WHERE (:applyTags = 0 OR id IN (SELECT taskId FROM task_tags WHERE tagId IN (:tagIds))) ORDER BY pinned DESC, done ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithTagsAndSubtasks>> observeAllWithTagsAndSubtasks(int applyTags, List<Long> tagIds);
    @Transaction
    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL AND (:applyTags = 0 OR id IN (SELECT taskId FROM task_tags WHERE tagId IN (:tagIds))) ORDER BY pinned DESC, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithTagsAndSubtasks>> observeUndoneWithTagsAndSubtasksWithDeadline(int applyTags, List<Long> tagIds);

    @Transaction
    @Query("SELECT * FROM tasks WHERE done = 0 AND groupId IS NULL AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') AND (:applyTags = 0 OR id IN (SELECT taskId FROM task_tags WHERE tagId IN (:tagIds))) ORDER BY pinned DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithTagsAndSubtasks>> searchUndoneWithTagsAndSubtasksInInbox(String q, int applyTags, List<Long> tagIds);

    @Transaction
    @Query("SELECT * FROM tasks WHERE done = 0 AND groupId = :groupId AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') AND (:applyTags = 0 OR id IN (SELECT taskId FROM task_tags WHERE tagId IN (:tagIds))) ORDER BY pinned DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithTagsAndSubtasks>> searchUndoneWithTagsAndSubtasksInGroup(long groupId, String q, int applyTags, List<Long> tagIds);

    @Transaction
    @Query("SELECT * FROM tasks WHERE done = 0 AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') AND (:applyTags = 0 OR id IN (SELECT taskId FROM task_tags WHERE tagId IN (:tagIds))) ORDER BY pinned DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithTagsAndSubtasks>> searchUndoneWithTagsAndSubtasksAll(String q, int applyTags, List<Long> tagIds);

    @Transaction
    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL AND (title LIKE '%'||:q||'%' OR description LIKE '%'||:q||'%') AND (:applyTags = 0 OR id IN (SELECT taskId FROM task_tags WHERE tagId IN (:tagIds))) ORDER BY pinned DESC, dueAt ASC, createdAt DESC")
    LiveData<List<TaskWithTagsAndSubtasks>> searchUndoneWithTagsAndSubtasksWithDeadline(String q, int applyTags, List<Long> tagIds);
}