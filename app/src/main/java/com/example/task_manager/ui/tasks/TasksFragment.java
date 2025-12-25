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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
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
import com.example.task_manager.data.TaskWithTagsAndSubtasks;
import com.example.task_manager.data.TagDao;
import com.example.task_manager.data.TagEntity;
import com.example.task_manager.ui.groups.GroupsActivity;
import com.example.task_manager.ui.taskdetail.TaskDetailActivity;
import com.example.task_manager.ui.common.UiStateViewModel;
import com.example.task_manager.databinding.FragmentTasksBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

public class TasksFragment extends Fragment {

    private static final String PREFS_NAME = "tasks_prefs";
    private static final String PREF_SELECTED_GROUP_ID = "selected_group_id";
    private static final String STATE_QUERY = "state_query";

    private FragmentTasksBinding binding;
    private TasksAdapter adapter;
    private TaskDao taskDao;
    private SubtaskDao subtaskDao;
    private GroupDao groupDao;
    private TagDao tagDao;
    private ExecutorService ioExecutor;
    private UiStateViewModel uiState;
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
    private LiveData<List<TaskWithTagsAndSubtasks>> tasksLiveData;
    @Nullable
    private Long selectedGroupId;
    private ArrayAdapter<GroupItem> groupAdapter;
    private final List<GroupItem> groupItems = new ArrayList<>();
    private SharedPreferences preferences;
    private String currentQuery = "";
    private final Set<Long> selectedTagFilter = new HashSet<>();
    private List<TagEntity> availableTags = new ArrayList<>();

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
        tagDao = AppDatabase.getInstance(requireContext()).tagDao();
        uiState = new ViewModelProvider(requireActivity()).get(UiStateViewModel.class);
        ioExecutor = Executors.newSingleThreadExecutor();
        preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeState(savedInstanceState);
        binding.emptyState.setVisibility(View.GONE);
        tagDao.observeAllOrdered().observe(getViewLifecycleOwner(), tags -> availableTags = tags == null ? new ArrayList<>() : new ArrayList<>(tags));
        setupGroupSelector();
        setupRecyclerView();
        observeUiState();
        setupMenu();
        setupFab();
        applyWindowInsets();
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
        tasksLiveData = resolveTasksSource();
        tasksLiveData.observe(getViewLifecycleOwner(), tasks -> {
            adapter.submitList(tasks);
            boolean isEmpty = tasks == null || tasks.isEmpty();
            binding.tasksList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            updateEmptyStateText(isEmpty);
        });
    }

    private LiveData<List<TaskWithTagsAndSubtasks>> resolveTasksSource() {
        boolean applyTags = !selectedTagFilter.isEmpty();
        List<Long> tagIds = new ArrayList<>(selectedTagFilter);
        int applyFlag = applyTags ? 1 : 0;
        if (TextUtils.isEmpty(currentQuery)) {
            return taskDao.observeAllWithTagsAndSubtasksByGroup(selectedGroupId, applyFlag, tagIds);
        }
        if (selectedGroupId == null) {
            return taskDao.searchUndoneWithTagsAndSubtasksInInbox(currentQuery, applyFlag, tagIds);
        }
        return taskDao.searchUndoneWithTagsAndSubtasksInGroup(selectedGroupId, currentQuery, applyFlag, tagIds);
    }

    private void updateEmptyStateText(boolean isEmpty) {
        if (!isEmpty) {
            binding.emptyTitle.setText(R.string.empty_tasks_title);
            binding.emptySubtitle.setText(R.string.empty_tasks_subtitle);
            return;
        }
        if (TextUtils.isEmpty(currentQuery)) {
            binding.emptyTitle.setText(R.string.empty_tasks_title);
            binding.emptySubtitle.setText(R.string.empty_tasks_subtitle);
        } else {
            binding.emptyTitle.setText(R.string.empty_search_title);
            binding.emptySubtitle.setText(R.string.empty_search_subtitle);
        }
    }

    private void applyQuery(@Nullable String query) {
        String normalized = query == null ? "" : query.trim();
        String stored = uiState.getSearchQuery().getValue();
        if (normalized.equals(stored == null ? "" : stored)) {
            return;
        }
        uiState.setSearchQuery(normalized);
    }

    private void setupFab() {
        View fab = binding.fab;
        fab.setOnClickListener(v -> showTaskDialog(null));
    }

    private void showTagFilterDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tags, null, false);
        EditText input = dialogView.findViewById(R.id.input_tag_name);
        View addButton = dialogView.findViewById(R.id.add_tag_button);
        ViewGroup container = dialogView.findViewById(R.id.tag_list_container);

        Set<Long> selectedIds = new HashSet<>(selectedTagFilter);

        Runnable rebuild = () -> {
            container.removeAllViews();
            if (availableTags.isEmpty()) {
                TextView empty = new TextView(requireContext());
                empty.setText(R.string.no_tags);
                container.addView(empty);
                return;
            }
            for (TagEntity tag : availableTags) {
                MaterialCheckBox checkBox = new MaterialCheckBox(requireContext());
                checkBox.setText(tag.getName());
                checkBox.setChecked(selectedIds.contains(tag.getId()));
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedIds.add(tag.getId());
                    } else {
                        selectedIds.remove(tag.getId());
                    }
                });
                container.addView(checkBox);
            }
        };
        rebuild.run();

        addButton.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                return;
            }
            ioExecutor.execute(() -> {
                TagEntity existing = tagDao.findByNameSync(name);
                TagEntity resolved = existing;
                if (resolved == null) {
                    tagDao.insert(new TagEntity(name));
                    resolved = tagDao.findByNameSync(name);
                }
                TagEntity finalResolved = resolved;
                requireActivity().runOnUiThread(() -> {
                    input.setText("");
                    if (finalResolved != null) {
                        boolean existsInList = false;
                        for (TagEntity tag : availableTags) {
                            if (tag.getId() == finalResolved.getId()) {
                                existsInList = true;
                                break;
                            }
                        }
                        if (!existsInList) {
                            availableTags.add(finalResolved);
                        }
                        selectedIds.add(finalResolved.getId());
                    }
                    rebuild.run();
                });
            });
        });

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tags_filter_title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    selectedTagFilter.clear();
                    selectedTagFilter.addAll(selectedIds);
                    observeTasks();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
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
                viewHolder.itemView.post(() -> adapter.notifyItemChanged(position));
                TaskWithTagsAndSubtasks taskWithSubtasks = adapter.getItem(position);
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

    private void handleDeleteSwipe(TaskWithTagsAndSubtasks taskWithSubtasks) {
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
        binding.groupSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                GroupItem item = groupAdapter.getItem(position);
                if (item != null) {
                    uiState.setSelectedGroupId(item.id);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No-op.
            }
        });

        groupDao.observeAllOrdered().observe(getViewLifecycleOwner(), groups -> {
            groupItems.clear();
            groupItems.add(new GroupItem(null, getString(R.string.group_inbox), null));
            if (groups != null) {
                for (GroupEntity group : groups) {
                    groupItems.add(new GroupItem(group.getId(), group.getName(), group.getColor()));
                }
            }
            updateGroupAdapter();
            ensureSelectedGroupAvailable();
        });
    }

    private void updateGroupAdapter() {
        groupAdapter.clear();
        groupAdapter.addAll(groupItems);
        groupAdapter.notifyDataSetChanged();
    }

    private void setupMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_tasks_actions, menu);
                MenuItem searchItem = menu.findItem(R.id.action_search);
                SearchView searchView = (SearchView) searchItem.getActionView();
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        applyQuery(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        applyQuery(newText);
                        return true;
                    }
                });
                searchView.setOnCloseListener(() -> {
                    applyQuery("");
                    return false;
                });
                if (!TextUtils.isEmpty(currentQuery)) {
                    searchItem.expandActionView();
                    searchView.setQuery(currentQuery, false);
                    searchView.clearFocus();
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_groups) {
                    startActivity(GroupsActivity.createIntent(requireContext()));
                    return true;
                } else if (menuItem.getItemId() == R.id.action_tags) {
                    showTagFilterDialog();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void applyWindowInsets() {
        final int listPaddingStart = binding.tasksList.getPaddingStart();
        final int listPaddingTop = binding.tasksList.getPaddingTop();
        final int listPaddingEnd = binding.tasksList.getPaddingEnd();
        final int listPaddingBottom = binding.tasksList.getPaddingBottom();

        final int emptyPaddingStart = binding.emptyState.getPaddingStart();
        final int emptyPaddingTop = binding.emptyState.getPaddingTop();
        final int emptyPaddingEnd = binding.emptyState.getPaddingEnd();
        final int emptyPaddingBottom = binding.emptyState.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            binding.tasksList.setPaddingRelative(
                    listPaddingStart,
                    listPaddingTop,
                    listPaddingEnd,
                    listPaddingBottom + systemInsets.bottom
            );

            binding.emptyState.setPaddingRelative(
                    emptyPaddingStart,
                    emptyPaddingTop,
                    emptyPaddingEnd,
                    emptyPaddingBottom + systemInsets.bottom
            );

            binding.fab.setTranslationY(-systemInsets.bottom);

            return insets;
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, currentQuery);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.tasksList.setAdapter(null);
            binding.groupSelector.setOnItemSelectedListener(null);
        }
        binding = null;
        adapter = null;
        groupAdapter = null;
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
        @Nullable
        final Integer color;

        GroupItem(@Nullable Long id, String name, @Nullable Integer color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    private void observeUiState() {
        uiState.getSelectedGroupId().observe(getViewLifecycleOwner(), groupId -> {
            selectedGroupId = groupId;
            saveSelectedGroup(groupId);
            observeTasks();
            updateGroupSelectionText();
        });
        uiState.getSearchQuery().observe(getViewLifecycleOwner(), query -> {
            String normalized = query == null ? "" : query;
            if (!normalized.equals(currentQuery)) {
                currentQuery = normalized;
                observeTasks();
            }
        });
    }

    private void ensureSelectedGroupAvailable() {
        if (groupItems.isEmpty()) {
            return;
        }
        boolean selectionExists = false;
        for (GroupItem item : groupItems) {
            if ((item.id == null && selectedGroupId == null)
                    || (item.id != null && item.id.equals(selectedGroupId))) {
                selectionExists = true;
                break;
            }
        }
        if (!selectionExists) {
            uiState.setSelectedGroupId(null);
            selectedGroupId = null;
        }
        updateGroupSelectionText();
    }

    private void updateGroupSelectionText() {
        if (binding == null || groupItems.isEmpty()) {
            return;
        }
        GroupItem selected = findSelectedGroupItem();
        if (selected == null) {
            return;
        }
        int selectedPosition = groupAdapter.getPosition(selected);
        if (selectedPosition == AdapterView.INVALID_POSITION) {
            return;
        }
        if (binding.groupSelector.getSelectedItemPosition() != selectedPosition) {
            binding.groupSelector.setSelection(selectedPosition);
        }
    }

    @Nullable
    private GroupItem findSelectedGroupItem() {
        for (GroupItem item : groupItems) {
            if ((item.id == null && selectedGroupId == null)
                    || (item.id != null && item.id.equals(selectedGroupId))) {
                return item;
            }
        }
        return groupItems.isEmpty() ? null : groupItems.get(0);
    }

    private void initializeState(@Nullable Bundle savedInstanceState) {
        if (!uiState.isSelectedGroupInitialized()) {
            Long restored = null;
            if (preferences.contains(PREF_SELECTED_GROUP_ID)) {
                restored = preferences.getLong(PREF_SELECTED_GROUP_ID, 0);
            }
            uiState.setSelectedGroupId(restored);
        }
        if (!uiState.isSearchQueryInitialized()) {
            String restoredQuery = savedInstanceState == null ? "" : savedInstanceState.getString(STATE_QUERY, "");
            uiState.setSearchQuery(restoredQuery);
        }
        currentQuery = uiState.getSearchQuery().getValue() == null ? "" : uiState.getSearchQuery().getValue();
        selectedGroupId = uiState.getSelectedGroupId().getValue();
    }

    private void saveSelectedGroup(@Nullable Long groupId) {
        SharedPreferences.Editor editor = preferences.edit();
        if (groupId == null) {
            editor.remove(PREF_SELECTED_GROUP_ID);
        } else {
            editor.putLong(PREF_SELECTED_GROUP_ID, groupId);
        }
        editor.apply();
    }
}