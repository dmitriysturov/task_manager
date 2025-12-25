package com.example.task_manager.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface TaskTagDao {

    @Transaction
    @Query("SELECT tags.* FROM tags INNER JOIN task_tags ON tags.id = task_tags.tagId WHERE task_tags.taskId = :taskId ORDER BY tags.name ASC")
    LiveData<List<TagEntity>> observeTagsForTask(long taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCrossRef(TaskTagCrossRef ref);

    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    void deleteCrossRef(long taskId, long tagId);

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    void deleteForTask(long taskId);

    @Transaction
    default void replaceTagsForTask(long taskId, List<Long> tagIds) {
        deleteForTask(taskId);
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            insertCrossRef(new TaskTagCrossRef(taskId, tagId));
        }
    }
}