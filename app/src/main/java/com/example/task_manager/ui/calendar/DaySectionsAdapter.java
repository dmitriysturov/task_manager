package com.example.task_manager.ui.calendar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class DaySectionsAdapter extends RecyclerView.Adapter<DaySectionsAdapter.DaySectionViewHolder> {

    public interface OnTaskClickListener {
        void onTaskClick(TaskEntity task);
    }

    private final List<DaySection> sections = new ArrayList<>();
    private final TaskDao taskDao;
    private final ExecutorService ioExecutor;
    private final OnTaskClickListener clickListener;
    private final DateTimeFormatter dayTitleFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault());
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
    private final ZoneId zoneId = ZoneId.systemDefault();

    public DaySectionsAdapter(TaskDao taskDao, ExecutorService ioExecutor, OnTaskClickListener clickListener) {
        this.taskDao = taskDao;
        this.ioExecutor = ioExecutor;
        this.clickListener = clickListener;
    }

    public void submitList(List<DaySection> newSections) {
        sections.clear();
        if (newSections != null) {
            sections.addAll(newSections);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DaySectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_day_section, parent, false);
        return new DaySectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DaySectionViewHolder holder, int position) {
        DaySection section = sections.get(position);
        holder.dayTitle.setText(capitalize(dayTitleFormatter.format(section.getDate())));
        holder.bindTasks(section.getTasks());
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }

    class DaySectionViewHolder extends RecyclerView.ViewHolder {
        final TextView dayTitle;
        final TextView emptyText;
        final RecyclerView tasksList;
        final DayTasksAdapter tasksAdapter;

        DaySectionViewHolder(@NonNull View itemView) {
            super(itemView);
            dayTitle = itemView.findViewById(R.id.day_title);
            emptyText = itemView.findViewById(R.id.day_empty_text);
            tasksList = itemView.findViewById(R.id.day_tasks_list);
            tasksAdapter = new DayTasksAdapter();
            tasksList.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            tasksList.setAdapter(tasksAdapter);
        }

        void bindTasks(List<TaskEntity> tasks) {
            boolean isEmpty = tasks == null || tasks.isEmpty();
            emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            tasksAdapter.submitList(tasks);
        }
    }

    class DayTasksAdapter extends RecyclerView.Adapter<DayTasksAdapter.TaskViewHolder> {

        private final List<TaskEntity> tasks = new ArrayList<>();

        void submitList(List<TaskEntity> newTasks) {
            tasks.clear();
            if (newTasks != null) {
                tasks.addAll(newTasks);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_task, parent, false);
            return new TaskViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
            TaskEntity task = tasks.get(position);
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setText(task.getTitle());
            holder.checkBox.setChecked(task.isDone());

            Long dueAt = task.getDueAt();
            if (dueAt != null) {
                holder.timeText.setText(timeFormatter.format(Instant.ofEpochMilli(dueAt).atZone(zoneId)));
            } else {
                holder.timeText.setText(holder.itemView.getContext().getString(R.string.no_deadline_label));
            }

            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                task.setDone(isChecked);
                ioExecutor.execute(() -> taskDao.update(task));
            });

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTaskClick(task);
                }
            });
        }

        @Override
        public int getItemCount() {
            return tasks.size();
        }

        class TaskViewHolder extends RecyclerView.ViewHolder {
            final MaterialCheckBox checkBox;
            final TextView timeText;

            TaskViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.calendar_task_checkbox);
                timeText = itemView.findViewById(R.id.calendar_task_time);
            }
        }
    }
}