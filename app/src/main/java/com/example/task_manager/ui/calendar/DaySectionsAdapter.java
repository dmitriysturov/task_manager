package com.example.task_manager.ui.calendar;

import android.graphics.Color;
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
import com.example.task_manager.data.TaskWithGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;

import java.time.Instant;
import java.time.LocalDate;
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

    public enum SelectionMode {
        WEEK,
        DAY
    }

    private final List<DaySection> sections = new ArrayList<>();
    private final TaskDao taskDao;
    private final ExecutorService ioExecutor;
    private final OnTaskClickListener clickListener;
    private final DateTimeFormatter dayTitleFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault());
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
    private final ZoneId zoneId = ZoneId.systemDefault();

    private SelectionMode selectionMode = SelectionMode.WEEK;
    private LocalDate today = LocalDate.now();
    private LocalDate selectedDay;

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
        holder.bindSelection(section.getDate(), selectionMode, today, selectedDay);
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

    public void updateSelection(SelectionMode mode, LocalDate today, LocalDate selectedDay) {
        this.selectionMode = mode;
        this.today = today;
        this.selectedDay = selectedDay;
        notifyDataSetChanged();
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

        void bindTasks(List<TaskWithGroup> tasks) {
            boolean isEmpty = tasks == null || tasks.isEmpty();
            emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            tasksAdapter.submitList(tasks);
        }

        void bindSelection(LocalDate date, SelectionMode mode, LocalDate today, LocalDate selectedDay) {
            dayTitle.setSelected(false);
            dayTitle.setBackgroundColor(Color.TRANSPARENT);
            int defaultTextColor = MaterialColors.getColor(dayTitle, com.google.android.material.R.attr.colorOnSurface);
            dayTitle.setTextColor(defaultTextColor);

            boolean highlight = false;
            if (mode == SelectionMode.WEEK) {
                highlight = date.equals(today);
            } else if (selectedDay != null) {
                highlight = date.equals(selectedDay);
            } else {
                highlight = date.equals(today);
            }

            if (highlight) {
                int background = MaterialColors.getColor(dayTitle, com.google.android.material.R.attr.colorPrimaryContainer);
                int textColor = MaterialColors.getColor(dayTitle, com.google.android.material.R.attr.colorOnPrimaryContainer);
                dayTitle.setSelected(true);
                dayTitle.setBackgroundColor(background);
                dayTitle.setTextColor(textColor);
            }
        }
    }

    class DayTasksAdapter extends RecyclerView.Adapter<DayTasksAdapter.TaskViewHolder> {

        private final List<TaskWithGroup> tasks = new ArrayList<>();

        void submitList(List<TaskWithGroup> newTasks) {
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
            TaskWithGroup taskWithGroup = tasks.get(position);
            TaskEntity task = taskWithGroup.task;
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setText(task.getTitle());
            holder.checkBox.setChecked(task.isDone());

            Long dueAt = task.getDueAt();
            if (dueAt != null) {
                holder.timeText.setText(timeFormatter.format(Instant.ofEpochMilli(dueAt).atZone(zoneId)));
            } else {
                holder.timeText.setText(holder.itemView.getContext().getString(R.string.no_deadline_label));
            }

            String groupName = taskWithGroup.groupName;
            holder.groupText.setText(groupName == null ? "" : groupName);
            int defaultMarkerColor = MaterialColors.getColor(holder.groupMarker, com.google.android.material.R.attr.colorSecondary);
            Integer groupColor = taskWithGroup.groupColor;
            holder.groupMarker.setBackgroundColor(groupColor != null && groupColor != 0 ? groupColor : defaultMarkerColor);

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
            final View groupMarker;
            final TextView groupText;

            TaskViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.calendar_task_checkbox);
                timeText = itemView.findViewById(R.id.calendar_task_time);
                groupMarker = itemView.findViewById(R.id.calendar_task_group_marker);
                groupText = itemView.findViewById(R.id.calendar_task_group_text);
            }
        }
    }
}