package com.example.task_manager.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TagDao {

    @Query("SELECT * FROM tags")
    LiveData<List<TagEntity>> observeAll();

    @Query("SELECT * FROM tags ORDER BY name ASC")
    LiveData<List<TagEntity>> observeAllOrdered();

    @Query("SELECT * FROM tags WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    TagEntity findByNameSync(String name);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(TagEntity tag);

    @Delete
    void delete(TagEntity tag);
}