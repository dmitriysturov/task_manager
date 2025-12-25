package com.example.task_manager.ui.calendar;

import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.data.TaskWithGroup;

import java.time.LocalDate;
import java.util.List;

public class DaySection {
    private final LocalDate date;
    private final List<TaskWithGroup> tasks;

    public DaySection(LocalDate date, List<TaskWithGroup> tasks) {
        this.date = date;
        this.tasks = tasks;
    }

    public LocalDate getDate() {
        return date;
    }

    public List<TaskWithGroup> getTasks() {
        return tasks;
    }
}