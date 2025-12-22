package com.example.task_manager.ui.calendar;

import com.example.task_manager.data.TaskEntity;

import java.time.LocalDate;
import java.util.List;

public class DaySection {
    private final LocalDate date;
    private final List<TaskEntity> tasks;

    public DaySection(LocalDate date, List<TaskEntity> tasks) {
        this.date = date;
        this.tasks = tasks;
    }

    public LocalDate getDate() {
        return date;
    }

    public List<TaskEntity> getTasks() {
        return tasks;
    }
}