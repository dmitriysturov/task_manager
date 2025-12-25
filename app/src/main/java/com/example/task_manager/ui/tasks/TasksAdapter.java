package com.example.task_manager.ui.tasks;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.data.SubtaskDao;
import com.example.task_manager.data.SubtaskEntity;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.data.TaskWithTagsAndSubtasks;
import com.example.task_manager.data.TagEntity;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    private final List<TaskWithTagsAndSubtasks> items = new ArrayList<>();
    private final TaskDao taskDao;
    private final SubtaskDao subtaskDao;
    private final ExecutorService ioExecutor;
    private final OnTaskLongClickListener longClickListener;
    private final OnTaskClickListener clickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    private final Set<Long> expandedTaskIds = new HashSet<>();
    private boolean showGroupName;

    public TasksAdapter(TaskDao taskDao, SubtaskDao subtaskDao, ExecutorService ioExecutor, OnTaskLongClickListener longClickListener, OnTaskClickListener clickListener) {
        this.taskDao = taskDao;
        this.subtaskDao = subtaskDao;
        this.ioExecutor = ioExecutor;
        this.longClickListener = longClickListener;
        this.clickListener = clickListener;
        setHasStableIds(true);
    }

    public void setShowGroupName(boolean showGroupName) {
        if (this.showGroupName == showGroupName) {
            return;
        }
        this.showGroupName = showGroupName;
        notifyDataSetChanged();
    }

    public void submitList(List<TaskWithTagsAndSubtasks> newItems) {
        List<TaskWithTagsAndSubtasks> safeNewItems = newItems == null ? new ArrayList<>() : new ArrayList<>(newItems);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new TaskDiffCallback(items, safeNewItems));
        items.clear();
        items.addAll(safeNewItems);
        diffResult.dispatchUpdatesTo(this);
    }

    public TaskWithTagsAndSubtasks getItem(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TaskWithTagsAndSubtasks taskWithSubtasks = items.get(position);
        TaskEntity task = taskWithSubtasks.task;
        holder.title.setText(task.getTitle());
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(task.isDone());
        Long dueAt = task.getDueAt();
        String createdAtFormatted = holder.itemView.getContext().getString(R.string.created_at_label, dateFormat.format(new Date(task.getCreatedAt())));
        holder.createdText.setText(createdAtFormatted);
        holder.createdText.setTextColor(MaterialColors.getColor(holder.createdText, com.google.android.material.R.attr.colorOnSurfaceVariant));
        if (dueAt != null) {
            String formatted = dateFormat.format(new Date(dueAt));
            boolean overdue = dueAt < System.currentTimeMillis();
            int deadlineColor = MaterialColors.getColor(
                    holder.deadlineText,
                    overdue ? android.R.attr.colorError : com.google.android.material.R.attr.colorOnSurfaceVariant
            );
            String deadlineText = holder.itemView.getContext().getString(R.string.deadline_label, formatted);
            holder.deadlineText.setText(deadlineText);
            holder.deadlineText.setTextColor(deadlineColor);
        } else {
            String noDeadline = holder.itemView.getContext().getString(R.string.no_deadline_label);
            holder.deadlineText.setText(noDeadline);
            holder.deadlineText.setTextColor(MaterialColors.getColor(holder.deadlineText, com.google.android.material.R.attr.colorOnSurfaceVariant));
        }

        if (showGroupName) {
            String groupName = taskWithSubtasks.group != null
                    ? taskWithSubtasks.group.getName()
                    : holder.itemView.getContext().getString(R.string.group_ungrouped);
            holder.groupText.setText(groupName);
            holder.groupText.setVisibility(View.VISIBLE);
        } else {
            holder.groupText.setVisibility(View.GONE);
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
        holder.expandButton.setVisibility(View.VISIBLE);

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
        bindTags(holder, taskWithSubtasks.tags);
    }

    private void bindTags(TaskViewHolder holder, List<TagEntity> tags) {
        holder.tagsGroup.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            holder.tagsGroup.setVisibility(View.GONE);
            return;
        }
        holder.tagsGroup.setVisibility(View.VISIBLE);
        Context context = holder.itemView.getContext();
        List<TagEntity> sortedTags = new ArrayList<>(tags);
        Collections.sort(sortedTags, Comparator.comparing(TagEntity::getName, String.CASE_INSENSITIVE_ORDER));
        int count = Math.min(sortedTags.size(), 2);
        for (int i = 0; i < count; i++) {
            if (i == 1 && sortedTags.size() > 2) {
                int remaining = sortedTags.size() - 1;
                holder.tagsGroup.addView(createTagChip(context, "+" + remaining, true));
                return;
            }
            TagEntity tag = sortedTags.get(i);
            holder.tagsGroup.addView(createTagChip(context, tag.getName(), false));
        }
    }

    private Chip createTagChip(Context context, String text, boolean isSummary) {
        Chip chip = new Chip(context, null, com.google.android.material.R.attr.chipStyle);
        chip.setText(text);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(0f);
        int containerColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceVariant);
        int onContainerColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(containerColor));
        chip.setTextColor(onContainerColor);
        chip.setTextAppearanceResource(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        return chip;
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
    public long getItemId(int position) {
        return items.get(position).task.getId();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        final MaterialCheckBox checkBox;
        final TextView title;
        final TextView deadlineText;
        final TextView createdText;
        final TextView groupText;
        final ImageButton expandButton;
        final View subtasksContainer;
        final RecyclerView subtasksList;
        final View addSubtaskButton;
        final SubtaskMiniAdapter subtaskMiniAdapter;
        final ChipGroup tagsGroup;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.task_checkbox);
            title = itemView.findViewById(R.id.task_title);
            deadlineText = itemView.findViewById(R.id.deadline_text);
            createdText = itemView.findViewById(R.id.created_text);
            groupText = itemView.findViewById(R.id.task_group_text);
            expandButton = itemView.findViewById(R.id.expand_button);
            subtasksContainer = itemView.findViewById(R.id.subtasks_container);
            subtasksList = itemView.findViewById(R.id.subtasks_list);
            addSubtaskButton = itemView.findViewById(R.id.add_subtask_button);
            tagsGroup = itemView.findViewById(R.id.task_tags_group);
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

    private static class TaskDiffCallback extends DiffUtil.Callback {
        private final List<TaskWithTagsAndSubtasks> oldList;
        private final List<TaskWithTagsAndSubtasks> newList;

        TaskDiffCallback(List<TaskWithTagsAndSubtasks> oldList, List<TaskWithTagsAndSubtasks> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).task.getId() == newList.get(newItemPosition).task.getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            TaskWithTagsAndSubtasks oldItem = oldList.get(oldItemPosition);
            TaskWithTagsAndSubtasks newItem = newList.get(newItemPosition);
            TaskEntity oldTask = oldItem.task;
            TaskEntity newTask = newItem.task;
            boolean subtasksEqual = areSubtasksSame(oldItem.subtasks, newItem.subtasks);
            boolean tagsEqual = areTagsSame(oldItem.tags, newItem.tags);
            return oldTask.isDone() == newTask.isDone()
                    && oldTask.getTitle().equals(newTask.getTitle())
                    && ((oldTask.getDueAt() == null && newTask.getDueAt() == null) ||
                    (oldTask.getDueAt() != null && oldTask.getDueAt().equals(newTask.getDueAt())))
                    && oldTask.getDescription().equals(newTask.getDescription())
                    && ((oldTask.getGroupId() == null && newTask.getGroupId() == null) ||
                    (oldTask.getGroupId() != null && oldTask.getGroupId().equals(newTask.getGroupId())))
                    && oldTask.getCreatedAt() == newTask.getCreatedAt()
                    && subtasksEqual
                    && tagsEqual;
        }

        private boolean areSubtasksSame(List<SubtaskEntity> oldSubtasks, List<SubtaskEntity> newSubtasks) {
            if (oldSubtasks == null && newSubtasks == null) {
                return true;
            }
            if (oldSubtasks == null || newSubtasks == null || oldSubtasks.size() != newSubtasks.size()) {
                return false;
            }
            for (int i = 0; i < oldSubtasks.size(); i++) {
                SubtaskEntity oldSubtask = oldSubtasks.get(i);
                SubtaskEntity newSubtask = newSubtasks.get(i);
                if (oldSubtask.id != newSubtask.id
                        || oldSubtask.done != newSubtask.done
                        || oldSubtask.updatedAt != newSubtask.updatedAt
                        || !oldSubtask.title.equals(newSubtask.title)) {
                    return false;
                }
            }
            return true;
        }

        private boolean areTagsSame(List<TagEntity> oldTags, List<TagEntity> newTags) {
            if (oldTags == null && newTags == null) {
                return true;
            }
            if (oldTags == null || newTags == null || oldTags.size() != newTags.size()) {
                return false;
            }
            List<TagEntity> oldSorted = new ArrayList<>(oldTags);
            List<TagEntity> newSorted = new ArrayList<>(newTags);
            Collections.sort(oldSorted, Comparator.comparing(TagEntity::getName, String.CASE_INSENSITIVE_ORDER));
            Collections.sort(newSorted, Comparator.comparing(TagEntity::getName, String.CASE_INSENSITIVE_ORDER));
            for (int i = 0; i < oldSorted.size(); i++) {
                TagEntity oldTag = oldSorted.get(i);
                TagEntity newTag = newSorted.get(i);
                if (oldTag.getId() != newTag.getId() || !oldTag.getName().equals(newTag.getName())) {
                    return false;
                }
            }
            return true;
        }
    }
}