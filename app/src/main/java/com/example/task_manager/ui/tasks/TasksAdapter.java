package com.example.task_manager.ui.tasks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.text.SimpleDateFormat;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.TaskViewHolder> {

    public interface OnTaskLongClickListener {
        void onTaskLongClick(TaskEntity task);
    }

    public interface OnTaskClickListener {
        void onTaskClick(TaskEntity task);
    }

    private final List<TaskEntity> items = new ArrayList<>();
    private final TaskDao taskDao;
    private final ExecutorService ioExecutor;
    private final OnTaskLongClickListener longClickListener;
    private final OnTaskClickListener clickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public TasksAdapter(TaskDao taskDao, ExecutorService ioExecutor, OnTaskLongClickListener longClickListener, OnTaskClickListener clickListener) {
        this.taskDao = taskDao;
        this.ioExecutor = ioExecutor;
        this.longClickListener = longClickListener;
        this.clickListener = clickListener;
    }

    public void submitList(List<TaskEntity> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TaskEntity task = items.get(position);
        holder.title.setText(task.getTitle());
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(task.isDone());
        if (task.getDueAt() != null) {
            String formatted = dateFormat.format(new Date(task.getDueAt()));
            holder.deadline.setText(holder.itemView.getContext().getString(R.string.deadline_label, formatted));
        } else {
            holder.deadline.setText(R.string.no_deadline_label);
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

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onTaskLongClick(task);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final TextView title;
        final TextView deadline;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.task_checkbox);
            title = itemView.findViewById(R.id.task_title);
            deadline = itemView.findViewById(R.id.task_deadline);
        }
    }
}