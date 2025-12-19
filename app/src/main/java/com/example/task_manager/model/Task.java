package com.example.task_manager.model;

public class Task {

    private String title;
    private boolean done;

    public Task(String title) {
        this(title, false);
    }

    public Task(String title, boolean done) {
        this.title = title;
        this.done = done;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}