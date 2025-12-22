package com.example.task_manager.ui.tasks;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.data.SubtaskDao;
import com.example.task_manager.data.SubtaskEntity;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.data.TaskWithSubtasks;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.TaskViewHolder> {

    public interface OnTaskLongClickListener {
        void onTaskLongClick(TaskEntity task);
    }

    public interface OnTaskClickListener {
        void onTaskClick(TaskEntity task);
    }

    private final List<TaskWithSubtasks> items = new ArrayList<>();
    private final TaskDao taskDao;
    private final SubtaskDao subtaskDao;
    private final ExecutorService ioExecutor;
    private final OnTaskLongClickListener longClickListener;
    private final OnTaskClickListener clickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    private final Set<Long> expandedTaskIds = new HashSet<>();

    public TasksAdapter(TaskDao taskDao, SubtaskDao subtaskDao, ExecutorService ioExecutor, OnTaskLongClickListener longClickListener, OnTaskClickListener clickListener) {
        this.taskDao = taskDao;
        this.subtaskDao = subtaskDao;
        this.ioExecutor = ioExecutor;
        this.longClickListener = longClickListener;
        this.clickListener = clickListener;
    }

    public void submitList(List<TaskWithSubtasks> newItems) {
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
        TaskWithSubtasks taskWithSubtasks = items.get(position);
        TaskEntity task = taskWithSubtasks.task;
        holder.title.setText(task.getTitle());
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(task.isDone());
        Long dueAt = task.getDueAt();
        if (dueAt != null) {
            String formatted = dateFormat.format(new Date(dueAt));
            boolean overdue = dueAt < System.currentTimeMillis();
            int containerColor = MaterialColors.getColor(holder.deadlineChip,
                    overdue ? com.google.android.material.R.attr.colorErrorContainer : com.google.android.material.R.attr.colorSecondaryContainer);
            int onContainerColor = MaterialColors.getColor(holder.deadlineChip,
                    overdue ? com.google.android.material.R.attr.colorOnErrorContainer : com.google.android.material.R.attr.colorOnSecondaryContainer);
            holder.deadlineChip.setChipBackgroundColor(ColorStateList.valueOf(containerColor));
            holder.deadlineChip.setTextColor(onContainerColor);
            holder.deadlineChip.setChipIconTint(ColorStateList.valueOf(onContainerColor));
            holder.deadlineChip.setText(holder.itemView.getContext().getString(R.string.deadline_label, formatted));
            holder.deadlineChip.setVisibility(View.VISIBLE);
        } else {
            holder.deadlineChip.setVisibility(View.GONE);
        }

        if (task.getCreatedAt() > 0) {
            String formattedCreated = dateFormat.format(new Date(task.getCreatedAt()));
            holder.createdAtText.setVisibility(View.VISIBLE);
            holder.createdAtText.setText(holder.itemView.getContext().getString(R.string.created_at_label, formattedCreated));
        } else {
            holder.createdAtText.setVisibility(View.GONE);
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

        boolean expanded = expandedTaskIds.contains(task.getId());
        holder.subtasksContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.expandButton.setRotation(expanded ? 180f : 0f);

        holder.expandButton.setOnClickListener(v -> {
            if (expanded) {
                expandedTaskIds.remove(task.getId());
            } else {
                expandedTaskIds.add(task.getId());
            }
            notifyItemChanged(holder.getAdapterPosition());
        });

        holder.bindSubtasks(taskWithSubtasks.subtasks);
        holder.addSubtaskButton.setOnClickListener(v -> showAddSubtaskDialog(v.getContext(), task));
    }

    private void showAddSubtaskDialog(Context context, TaskEntity task) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_subtask, null, false);
        EditText titleInput = dialogView.findViewById(R.id.input_subtask_title);

        new AlertDialog.Builder(context)
                .setTitle(R.string.add_subtask)
                .setView(dialogView)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    SubtaskEntity subtask = new SubtaskEntity(task.getId(), title, false, now, now);
                    ioExecutor.execute(() -> subtaskDao.insert(subtask));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        final MaterialCheckBox checkBox;
        final TextView title;
        final Chip deadlineChip;
        final TextView createdAtText;
        final ImageButton expandButton;
        final View subtasksContainer;
        final RecyclerView subtasksList;
        final View addSubtaskButton;
        final SubtaskMiniAdapter subtaskMiniAdapter;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.task_checkbox);
            title = itemView.findViewById(R.id.task_title);
            deadlineChip = itemView.findViewById(R.id.deadline_chip);
            createdAtText = itemView.findViewById(R.id.created_at_text);
            expandButton = itemView.findViewById(R.id.expand_button);
            subtasksContainer = itemView.findViewById(R.id.subtasks_container);
            subtasksList = itemView.findViewById(R.id.subtasks_list);
            addSubtaskButton = itemView.findViewById(R.id.add_subtask_button);
            subtaskMiniAdapter = new SubtaskMiniAdapter(subtask -> {
                subtask.done = !subtask.done;
                subtask.updatedAt = System.currentTimeMillis();
                ioExecutor.execute(() -> subtaskDao.update(subtask));
            }, subtask -> {
                new AlertDialog.Builder(itemView.getContext())
                        .setMessage(R.string.delete_subtask_prompt)
                        .setPositiveButton(R.string.delete, (dialog, which) -> ioExecutor.execute(() -> subtaskDao.delete(subtask)))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
            subtasksList.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            subtasksList.setAdapter(subtaskMiniAdapter);
        }

        void bindSubtasks(List<SubtaskEntity> subtasks) {
            subtaskMiniAdapter.submitList(subtasks);
        }
    }
}