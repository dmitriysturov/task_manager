package com.example.task_manager.ui.tasks;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.data.AppDatabase;
import com.example.task_manager.data.GroupDao;
import com.example.task_manager.data.GroupEntity;
import com.example.task_manager.data.SubtaskDao;
import com.example.task_manager.data.SubtaskEntity;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.data.TaskWithSubtasks;
import com.example.task_manager.ui.groups.GroupsActivity;
import com.example.task_manager.ui.taskdetail.TaskDetailActivity;
import com.example.task_manager.databinding.FragmentTasksBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

public class TasksFragment extends Fragment {

    private static final String PREFS_NAME = "tasks_prefs";
    private static final String PREF_SELECTED_GROUP_ID = "selected_group_id";

    private FragmentTasksBinding binding;
    private TasksAdapter adapter;
    private TaskDao taskDao;
    private SubtaskDao subtaskDao;
    private GroupDao groupDao;
    private ExecutorService ioExecutor;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    private final Paint swipePaint = new Paint();
    private int deleteBackgroundColor;
    private int deleteIconColor;
    private int completeBackgroundColor;
    private int completeIconColor;
    private android.graphics.drawable.Drawable deleteIcon;
    private android.graphics.drawable.Drawable checkIcon;
    private android.graphics.drawable.Drawable undoIcon;
    private android.graphics.drawable.Drawable scheduleIcon;

    @Nullable
    private LiveData<List<TaskWithSubtasks>> tasksLiveData;
    @Nullable
    private Long selectedGroupId;
    private ArrayAdapter<String> groupAdapter;
    private final List<GroupItem> groupItems = new ArrayList<>();
    private SharedPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);
        taskDao = AppDatabase.getInstance(requireContext()).taskDao();
        subtaskDao = AppDatabase.getInstance(requireContext()).subtaskDao();
        groupDao = AppDatabase.getInstance(requireContext()).groupDao();
        ioExecutor = Executors.newSingleThreadExecutor();
        preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        restoreSelectedGroup();
        binding.emptyState.setVisibility(View.GONE);
        setupGroupSelector();
        setupRecyclerView();
        observeTasks();
        setupFab();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = binding.tasksList;
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TasksAdapter(taskDao, subtaskDao, ioExecutor, this::onTaskLongPressed, this::onTaskClicked);
        recyclerView.setAdapter(adapter);
        initSwipeResources();
        attachSwipeHelper(recyclerView);
    }

    private void observeTasks() {
        if (tasksLiveData != null) {
            tasksLiveData.removeObservers(getViewLifecycleOwner());
        }
        tasksLiveData = taskDao.observeAllWithSubtasksByGroup(selectedGroupId);
        tasksLiveData.observe(getViewLifecycleOwner(), tasks -> {
            adapter.submitList(tasks);
            boolean isEmpty = tasks == null || tasks.isEmpty();
            binding.tasksList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        });
    }

    private void setupFab() {
        View fab = binding.fab;
        fab.setOnClickListener(v -> showTaskDialog(null));
    }

    private void showTaskDialog(@Nullable TaskEntity existingTask) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_task, null, false);
        EditText input = dialogView.findViewById(R.id.input_title);
        Chip deadlineText = dialogView.findViewById(R.id.deadline_text);
        Button selectDeadlineButton = dialogView.findViewById(R.id.select_deadline_button);
        Button clearDeadlineButton = dialogView.findViewById(R.id.clear_deadline_button);

        final Long[] selectedDueAt = new Long[]{existingTask != null ? existingTask.getDueAt() : null};

        if (existingTask != null) {
            input.setText(existingTask.getTitle());
        }

        updateDeadlineText(deadlineText, selectedDueAt[0]);

        selectDeadlineButton.setOnClickListener(v -> openDeadlinePicker(selectedDueAt, deadlineText));
        clearDeadlineButton.setOnClickListener(v -> {
            selectedDueAt[0] = null;
            updateDeadlineText(deadlineText, null);
        });

        new AlertDialog.Builder(requireContext())
                .setTitle(existingTask == null ? R.string.add_task_title : R.string.edit_task_title)
                .setView(dialogView)
                .setPositiveButton(existingTask == null ? R.string.add : R.string.save, (dialog, which) -> {
                    String title = input.getText().toString().trim();
                    if (TextUtils.isEmpty(title)) {
                        return;
                    }
                    if (existingTask == null) {
                        TaskEntity task = new TaskEntity(title, false, System.currentTimeMillis(), selectedDueAt[0]);
                        task.setGroupId(selectedGroupId);
                        ioExecutor.execute(() -> taskDao.insert(task));
                    } else {
                        existingTask.setTitle(title);
                        existingTask.setDueAt(selectedDueAt[0]);
                        ioExecutor.execute(() -> taskDao.update(existingTask));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openDeadlinePicker(Long[] selectedDueAt, Chip deadlineText) {
        final Calendar calendar = Calendar.getInstance();
        if (selectedDueAt[0] != null) {
            calendar.setTimeInMillis(selectedDueAt[0]);
        }

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(), (view, year1, month1, dayOfMonth) -> {
            Calendar chosenDate = Calendar.getInstance();
            chosenDate.set(Calendar.YEAR, year1);
            chosenDate.set(Calendar.MONTH, month1);
            chosenDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            chosenDate.set(Calendar.SECOND, 0);
            chosenDate.set(Calendar.MILLISECOND, 0);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            TimePickerDialog timePickerDialog = new TimePickerDialog(requireContext(), (timeView, hourOfDay, minuteOfHour) -> {
                chosenDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                chosenDate.set(Calendar.MINUTE, minuteOfHour);
                selectedDueAt[0] = chosenDate.getTimeInMillis();
                updateDeadlineText(deadlineText, selectedDueAt[0]);
            }, hour, minute, DateFormat.is24HourFormat(requireContext()));

            timePickerDialog.show();
        }, year, month, day);

        datePickerDialog.show();
    }

    private void updateDeadlineText(Chip chip, @Nullable Long dueAt) {
        if (dueAt == null) {
            int containerColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceVariant);
            int onContainerColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant);
            chip.setChipBackgroundColor(ColorStateList.valueOf(containerColor));
            chip.setChipIconTint(ColorStateList.valueOf(onContainerColor));
            chip.setTextColor(onContainerColor);
            chip.setText(R.string.deadline_not_set);
            return;
        }
        String formatted = dateFormat.format(dueAt);
        boolean overdue = dueAt < System.currentTimeMillis();
        int containerColor = MaterialColors.getColor(chip,
                overdue ? com.google.android.material.R.attr.colorErrorContainer : com.google.android.material.R.attr.colorSecondaryContainer);
        int onContainerColor = MaterialColors.getColor(chip,
                overdue ? com.google.android.material.R.attr.colorOnErrorContainer : com.google.android.material.R.attr.colorOnSecondaryContainer);
        chip.setChipBackgroundColor(ColorStateList.valueOf(containerColor));
        chip.setChipIconTint(ColorStateList.valueOf(onContainerColor));
        chip.setTextColor(onContainerColor);
        chip.setText(getString(R.string.deadline_prefix, formatted));
    }

    private void onTaskLongPressed(TaskEntity task) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.delete_task_prompt)
                .setPositiveButton(R.string.delete, (dialog, which) -> removeTask(task))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void onTaskClicked(TaskEntity task) {
        startActivity(TaskDetailActivity.createIntent(requireContext(), task.getId()));
    }

    private void removeTask(TaskEntity task) {
        ioExecutor.execute(() -> taskDao.delete(task));
    }

    private void initSwipeResources() {
        deleteBackgroundColor = MaterialColors.getColor(binding.getRoot(), android.R.attr.colorError);
        deleteIconColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnError);
        completeBackgroundColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorPrimaryContainer);
        completeIconColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnPrimaryContainer);
        deleteIcon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_delete_24);
        checkIcon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_check_24);
        undoIcon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_undo_24);
        scheduleIcon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_schedule_24);
    }

    private void attachSwipeHelper(RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                TaskWithSubtasks taskWithSubtasks = adapter.getItem(position);
                if (direction == ItemTouchHelper.LEFT) {
                    handleDeleteSwipe(taskWithSubtasks);
                } else {
                    handleRightSwipe(taskWithSubtasks.task, position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    drawSwipeDecoration(c, viewHolder, dX);
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(simpleCallback).attachToRecyclerView(recyclerView);
    }

    private void handleDeleteSwipe(TaskWithSubtasks taskWithSubtasks) {
        TaskEntity task = taskWithSubtasks.task;
        TaskEntity backupTask = copyTask(task, false);
        List<SubtaskEntity> subtasksBackup = new ArrayList<>();
        if (taskWithSubtasks.subtasks != null) {
            for (SubtaskEntity subtask : taskWithSubtasks.subtasks) {
                SubtaskEntity clone = new SubtaskEntity(subtask.taskId, subtask.title, subtask.done, subtask.createdAt, subtask.updatedAt);
                subtasksBackup.add(clone);
            }
        }
        ioExecutor.execute(() -> taskDao.delete(task));
        Snackbar.make(binding.getRoot(), R.string.task_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> ioExecutor.execute(() -> {
                    TaskEntity restoredTask = copyTask(backupTask, false);
                    long newId = taskDao.insert(restoredTask);
                    for (SubtaskEntity subtask : subtasksBackup) {
                        subtask.taskId = newId;
                        subtaskDao.insert(subtask);
                    }
                }))
                .show();
    }

    private void handleRightSwipe(TaskEntity task, int position) {
        if (!task.isDone() && task.getDueAt() != null) {
            showQuickActionsDialog(task, position);
        } else {
            toggleDone(task, position);
        }
    }

    private void toggleDone(TaskEntity task, int position) {
        boolean previous = task.isDone();
        boolean updatedState = !previous;
        TaskEntity updated = copyTask(task, true);
        updated.setDone(updatedState);
        ioExecutor.execute(() -> taskDao.update(updated));
        Snackbar.make(binding.getRoot(), getString(updatedState ? R.string.task_marked_done : R.string.task_returned), Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> ioExecutor.execute(() -> {
                    TaskEntity revert = copyTask(task, true);
                    revert.setDone(previous);
                    taskDao.update(revert);
                }))
                .show();
        adapter.notifyItemChanged(position);
    }

    private void showQuickActionsDialog(TaskEntity task, int position) {
        String[] options = new String[]{
                getString(R.string.mark_done),
                getString(R.string.snooze_one_hour),
                getString(R.string.snooze_tomorrow)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.choose_quick_action)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        toggleDone(task, position);
                    } else if (which == 1) {
                        snoozeTask(task, position, true);
                    } else if (which == 2) {
                        snoozeTask(task, position, false);
                    }
                })
                .setOnDismissListener(dialog -> adapter.notifyItemChanged(position))
                .setOnCancelListener(dialog -> adapter.notifyItemChanged(position))
                .show();
    }

    private void snoozeTask(TaskEntity task, int position, boolean oneHour) {
        Long currentDue = task.getDueAt();
        if (currentDue == null) {
            adapter.notifyItemChanged(position);
            return;
        }
        long newDueAt;
        int messageRes;
        if (oneHour) {
            newDueAt = currentDue + 60 * 60 * 1000;
            messageRes = R.string.task_snoozed_hour;
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 9);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            newDueAt = calendar.getTimeInMillis();
            messageRes = R.string.task_snoozed_tomorrow;
        }
        TaskEntity updated = copyTask(task, true);
        updated.setDueAt(newDueAt);
        ioExecutor.execute(() -> taskDao.update(updated));
        Snackbar.make(binding.getRoot(), messageRes, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> ioExecutor.execute(() -> {
                    TaskEntity revert = copyTask(task, true);
                    revert.setDueAt(currentDue);
                    taskDao.update(revert);
                }))
                .show();
        adapter.notifyItemChanged(position);
    }

    private void drawSwipeDecoration(Canvas c, RecyclerView.ViewHolder viewHolder, float dX) {
        View itemView = viewHolder.itemView;
        int position = viewHolder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        TaskEntity task = adapter.getItem(position).task;
        boolean isRight = dX > 0;
        int iconMargin = (int) (16 * getResources().getDisplayMetrics().density);
        int iconSize = (int) (24 * getResources().getDisplayMetrics().density);
        swipePaint.setColor(isRight ? completeBackgroundColor : deleteBackgroundColor);
        float left = isRight ? itemView.getLeft() : itemView.getRight() + dX;
        float right = isRight ? itemView.getLeft() + dX : itemView.getRight();
        c.drawRect(left, itemView.getTop(), right, itemView.getBottom(), swipePaint);
        android.graphics.drawable.Drawable icon;
        if (isRight) {
            if (task.isDone()) {
                icon = undoIcon;
            } else if (task.getDueAt() != null) {
                icon = scheduleIcon;
            } else {
                icon = checkIcon;
            }
        } else {
            icon = deleteIcon;
        }
        if (icon == null) {
            return;
        }
        icon = icon.mutate();
        icon.setTint(isRight ? completeIconColor : deleteIconColor);
        int iconTop = itemView.getTop() + (itemView.getHeight() - iconSize) / 2;
        int iconBottom = iconTop + iconSize;
        int iconLeft;
        int iconRight;
        if (isRight) {
            iconLeft = itemView.getLeft() + iconMargin;
            iconRight = iconLeft + iconSize;
        } else {
            iconRight = itemView.getRight() - iconMargin;
            iconLeft = iconRight - iconSize;
        }
        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
        icon.draw(c);
    }

    private TaskEntity copyTask(TaskEntity task, boolean keepId) {
        TaskEntity copy = new TaskEntity(task.getTitle(), task.isDone(), task.getCreatedAt(), task.getDueAt(), task.getDescription());
        if (keepId) {
            copy.setId(task.getId());
        }
        copy.setGroupId(task.getGroupId());
        return copy;
    }

    private void setupGroupSelector() {
        groupAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
        binding.groupSelector.setAdapter(groupAdapter);
        binding.groupSelector.setOnClickListener(v -> binding.groupSelector.showDropDown());
        binding.groupSelector.setOnItemClickListener((parent, view, position, id) -> {
            GroupItem item = groupItems.get(position);
            updateSelectedGroup(item.id, true);
        });

        groupDao.observeAllOrdered().observe(getViewLifecycleOwner(), groups -> {
            groupItems.clear();
            groupItems.add(new GroupItem(null, getString(R.string.group_inbox)));
            if (groups != null) {
                for (GroupEntity group : groups) {
                    groupItems.add(new GroupItem(group.getId(), group.getName()));
                }
            }
            updateGroupAdapter();
            applySelectionToDropdown(false);
        });
        applySelectionToDropdown(false);
    }

    private void updateGroupAdapter() {
        List<String> names = new ArrayList<>();
        for (GroupItem item : groupItems) {
            names.add(item.name);
        }
        groupAdapter.clear();
        groupAdapter.addAll(names);
        groupAdapter.notifyDataSetChanged();
    }

    private void updateSelectedGroup(@Nullable Long groupId, boolean fromUser) {
        boolean changed = (selectedGroupId == null && groupId != null) || (selectedGroupId != null && !selectedGroupId.equals(groupId));
        selectedGroupId = groupId;
        saveSelectedGroup();
        if (changed || fromUser) {
            observeTasks();
        }
        applySelectionToDropdown(!fromUser);
    }

    private void applySelectionToDropdown(boolean triggerObserve) {
        if (groupItems.isEmpty()) {
            return;
        }
        Long previousSelection = selectedGroupId;
        int index = 0;
        boolean found = false;
        for (int i = 0; i < groupItems.size(); i++) {
            GroupItem item = groupItems.get(i);
            if ((item.id == null && selectedGroupId == null) || (item.id != null && item.id.equals(selectedGroupId))) {
                index = i;
                found = true;
                break;
            }
        }
        if (!found) {
            selectedGroupId = null;
            saveSelectedGroup();
            if (triggerObserve || previousSelection != null) {
                observeTasks();
            }
        }
        if (groupItems.size() > index) {
            GroupItem selected = groupItems.get(index);
            binding.groupSelector.setText(selected.name, false);
        }
    }

    private void restoreSelectedGroup() {
        if (preferences.contains(PREF_SELECTED_GROUP_ID)) {
            selectedGroupId = preferences.getLong(PREF_SELECTED_GROUP_ID, 0);
        } else {
            selectedGroupId = null;
        }
    }

    private void saveSelectedGroup() {
        SharedPreferences.Editor editor = preferences.edit();
        if (selectedGroupId == null) {
            editor.remove(PREF_SELECTED_GROUP_ID);
        } else {
            editor.putLong(PREF_SELECTED_GROUP_ID, selectedGroupId);
        }
        editor.apply();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_tasks_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_groups) {
            startActivity(GroupsActivity.createIntent(requireContext()));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }

    private static class GroupItem {
        @Nullable
        final Long id;
        final String name;

        GroupItem(@Nullable Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}