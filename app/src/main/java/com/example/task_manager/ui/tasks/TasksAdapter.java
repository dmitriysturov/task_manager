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
import java.util.List;
import java.util.concurrent.ExecutorService;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.TaskViewHolder> {

    public interface OnTaskLongClickListener {
        void onTaskLongClick(TaskEntity task);
    }

    private final List<TaskEntity> items = new ArrayList<>();
    private final TaskDao taskDao;
    private final ExecutorService ioExecutor;
    private final OnTaskLongClickListener longClickListener;

    public TasksAdapter(TaskDao taskDao, ExecutorService ioExecutor, OnTaskLongClickListener longClickListener) {
        this.taskDao = taskDao;
        this.ioExecutor = ioExecutor;
        this.longClickListener = longClickListener;
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

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            task.setDone(isChecked);
            ioExecutor.execute(() -> taskDao.update(task));
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

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.task_checkbox);
            title = itemView.findViewById(R.id.task_title);
        }
    }
}