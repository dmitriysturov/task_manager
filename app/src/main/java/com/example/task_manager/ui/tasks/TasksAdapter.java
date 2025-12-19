package com.example.task_manager.ui.tasks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.model.Task;

import java.util.List;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.TaskViewHolder> {

    public interface OnTaskInteractionListener {
        void onTaskChecked(int position, boolean isChecked);
        void onTaskLongPressed(int position);
    }

    private final List<Task> tasks;
    private final OnTaskInteractionListener listener;

    public TasksAdapter(List<Task> tasks, OnTaskInteractionListener listener) {
        this.tasks = tasks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.title.setText(task.getTitle());
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(task.isDone());

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onTaskChecked(holder.getAdapterPosition(), isChecked);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onTaskLongPressed(holder.getAdapterPosition());
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final TextView title;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.task_checkbox);
            title = itemView.findViewById(R.id.task_title);
        }
    }
}