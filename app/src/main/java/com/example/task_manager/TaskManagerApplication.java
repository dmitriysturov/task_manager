package com.example.task_manager;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class TaskManagerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}