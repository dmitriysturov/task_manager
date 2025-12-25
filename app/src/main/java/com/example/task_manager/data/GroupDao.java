package com.example.task_manager.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY orderIndex ASC, name ASC")
    LiveData<List<GroupEntity>> observeAllOrdered();

    @Query("SELECT * FROM groups")
    LiveData<List<GroupEntity>> observeAll();

    @Insert
    long insert(GroupEntity group);

    @Update
    void update(GroupEntity group);

    @Delete
    void delete(GroupEntity group);
}