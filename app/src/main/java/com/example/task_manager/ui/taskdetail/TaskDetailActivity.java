package com.example.task_manager.ui.taskdetail;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.task_manager.R;
import com.example.task_manager.data.AppDatabase;
import com.example.task_manager.data.SubtaskDao;
import com.example.task_manager.data.SubtaskEntity;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.data.TagDao;
import com.example.task_manager.data.TagEntity;
import com.example.task_manager.data.TaskTagDao;
import com.example.task_manager.databinding.ActivityTaskDetailBinding;
import com.example.task_manager.ui.tasks.SubtaskMiniAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskDetailActivity extends AppCompatActivity {

    private static final String EXTRA_TASK_ID = "task_id";

    public static Intent createIntent(Context context, long taskId) {
        Intent intent = new Intent(context, TaskDetailActivity.class);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        return intent;
    }

    private ActivityTaskDetailBinding binding;
    private TaskDao taskDao;
    private SubtaskDao subtaskDao;
    private TagDao tagDao;
    private TaskTagDao taskTagDao;
    private ExecutorService ioExecutor;
    private long taskId;
    @Nullable
    private Long selectedDueAt;
    private TaskEntity currentTask;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    private SubtaskMiniAdapter subtaskAdapter;
    private List<TagEntity> allTags = new ArrayList<>();
    private List<TagEntity> currentTags = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskId = getIntent().getLongExtra(EXTRA_TASK_ID, -1);
        if (taskId == -1) {
            finish();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityTaskDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        applyWindowInsets();

        taskDao = AppDatabase.getInstance(this).taskDao();
        subtaskDao = AppDatabase.getInstance(this).subtaskDao();
        tagDao = AppDatabase.getInstance(this).tagDao();
        taskTagDao = AppDatabase.getInstance(this).taskTagDao();
        ioExecutor = Executors.newSingleThreadExecutor();

        setupDeadlineButtons();
        setupSubtasks();
        setupTags();
        updateDeadlineChip(binding.deadlineChip, null);
        observeTask();
        observeSubtasks();
    }

    private void setupDeadlineButtons() {
        binding.selectDeadlineButton.setOnClickListener(v -> openDeadlinePicker());
        binding.resetDeadlineButton.setOnClickListener(v -> {
            selectedDueAt = null;
            updateDeadlineChip(binding.deadlineChip, null);
        });
    }

    private void setupSubtasks() {
        subtaskAdapter = new SubtaskMiniAdapter(subtask -> {
            subtask.done = !subtask.done;
            subtask.updatedAt = System.currentTimeMillis();
            ioExecutor.execute(() -> subtaskDao.update(subtask));
        }, subtask -> new AlertDialog.Builder(this)
                .setMessage(R.string.delete_subtask_prompt)
                .setPositiveButton(R.string.delete, (dialog, which) -> ioExecutor.execute(() -> subtaskDao.delete(subtask)))
                .setNegativeButton(R.string.cancel, null)
                .show());
        binding.subtasksList.setLayoutManager(new LinearLayoutManager(this));
        binding.subtasksList.setAdapter(subtaskAdapter);
        binding.subtasksList.setNestedScrollingEnabled(false);
        binding.addSubtaskFab.setOnClickListener(v -> showAddSubtaskDialog());
    }

    private void setupTags() {
        binding.editTagsButton.setOnClickListener(v -> showTagsDialog());
        tagDao.observeAllOrdered().observe(this, tags -> {
            allTags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
            updateTagChips();
        });
        taskTagDao.observeTagsForTask(taskId).observe(this, tags -> {
            currentTags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
            updateTagChips();
        });
    }

    private void applyWindowInsets() {
        final int appBarPaddingStart = binding.appBarLayout.getPaddingStart();
        final int appBarPaddingTop = binding.appBarLayout.getPaddingTop();
        final int appBarPaddingEnd = binding.appBarLayout.getPaddingEnd();
        final int appBarPaddingBottom = binding.appBarLayout.getPaddingBottom();

        final int scrollPaddingStart = binding.contentScroll.getPaddingStart();
        final int scrollPaddingTop = binding.contentScroll.getPaddingTop();
        final int scrollPaddingEnd = binding.contentScroll.getPaddingEnd();
        final int scrollPaddingBottom = binding.contentScroll.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            binding.appBarLayout.setPaddingRelative(
                    appBarPaddingStart,
                    appBarPaddingTop + systemInsets.top,
                    appBarPaddingEnd,
                    appBarPaddingBottom
            );

            binding.contentScroll.setPaddingRelative(
                    scrollPaddingStart,
                    scrollPaddingTop,
                    scrollPaddingEnd,
                    scrollPaddingBottom + systemInsets.bottom
            );

            binding.addSubtaskFab.setTranslationY(-systemInsets.bottom);

            return insets;
        });
    }


    private void showAddSubtaskDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_subtask, null, false);
        EditText input = dialogView.findViewById(R.id.input_subtask_title);

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_subtask_title)
                .setView(dialogView)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, R.string.subtask_title_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long now = System.currentTimeMillis();
                    SubtaskEntity subtask = new SubtaskEntity(taskId, title, false, now, now);
                    ioExecutor.execute(() -> subtaskDao.insert(subtask));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateTagChips() {
        binding.tagsChipGroup.removeAllViews();
        if (currentTags == null || currentTags.isEmpty()) {
            binding.tagsEmpty.setVisibility(View.VISIBLE);
            binding.tagsChipGroup.setVisibility(View.GONE);
            return;
        }
        binding.tagsEmpty.setVisibility(View.GONE);
        binding.tagsChipGroup.setVisibility(View.VISIBLE);
        List<TagEntity> sorted = new ArrayList<>(currentTags);
        sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (TagEntity tag : sorted) {
            Chip chip = createTagChip(tag.getName());
            binding.tagsChipGroup.addView(chip);
        }
    }

    private Chip createTagChip(String text) {
        Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
        chip.setText(text);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(0f);
        int containerColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceVariant);
        int onContainerColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant);
        chip.setChipBackgroundColor(ColorStateList.valueOf(containerColor));
        chip.setTextColor(onContainerColor);
        chip.setTextAppearanceResource(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        return chip;
    }

    private void showTagsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tags, null, false);
        EditText input = dialogView.findViewById(R.id.input_tag_name);
        View addButton = dialogView.findViewById(R.id.add_tag_button);
        ViewGroup container = dialogView.findViewById(R.id.tag_list_container);

        Set<Long> selectedIds = new HashSet<>();
        for (TagEntity tag : currentTags) {
            selectedIds.add(tag.getId());
        }

        Runnable rebuild = () -> {
            container.removeAllViews();
            if (allTags.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText(R.string.no_tags);
                container.addView(empty);
                return;
            }
            for (TagEntity tag : allTags) {
                MaterialCheckBox checkBox = new MaterialCheckBox(this);
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
                runOnUiThread(() -> {
                    input.setText("");
                    if (finalResolved != null) {
                        boolean existsInList = false;
                        for (TagEntity tag : allTags) {
                            if (tag.getId() == finalResolved.getId()) {
                                existsInList = true;
                                break;
                            }
                        }
                        if (!existsInList) {
                            allTags.add(finalResolved);
                        }
                        selectedIds.add(finalResolved.getId());
                    }
                    rebuild.run();
                });
            });
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.tags_header)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> ioExecutor.execute(() -> taskTagDao.replaceTagsForTask(taskId, new ArrayList<>(selectedIds))))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void observeTask() {
        taskDao.observeById(taskId).observe(this, task -> {
            if (task == null) {
                finish();
                return;
            }
            currentTask = task;
            binding.titleEdit.setText(task.getTitle());
            binding.descriptionEdit.setText(task.getDescription());
            selectedDueAt = task.getDueAt();
            updateDeadlineChip(binding.deadlineChip, selectedDueAt);
        });
    }

    private void observeSubtasks() {
        subtaskDao.observeByTaskId(taskId).observe(this, subtasks -> subtaskAdapter.submitList(subtasks));
    }

    private void openDeadlinePicker() {
        final Calendar calendar = Calendar.getInstance();
        if (selectedDueAt != null) {
            calendar.setTimeInMillis(selectedDueAt);
        }

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
            Calendar chosenDate = Calendar.getInstance();
            chosenDate.set(Calendar.YEAR, year1);
            chosenDate.set(Calendar.MONTH, month1);
            chosenDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            chosenDate.set(Calendar.SECOND, 0);
            chosenDate.set(Calendar.MILLISECOND, 0);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            TimePickerDialog timePickerDialog = new TimePickerDialog(this, (timeView, hourOfDay, minuteOfHour) -> {
                chosenDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                chosenDate.set(Calendar.MINUTE, minuteOfHour);
                selectedDueAt = chosenDate.getTimeInMillis();
                updateDeadlineChip(binding.deadlineChip, selectedDueAt);
            }, hour, minute, DateFormat.is24HourFormat(this));

            timePickerDialog.show();
        }, year, month, day);

        datePickerDialog.show();
    }

    private void updateDeadlineChip(Chip chip, @Nullable Long dueAt) {
        if (dueAt == null) {
            int containerColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceVariant);
            int onContainerColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant);
            chip.setChipBackgroundColor(ColorStateList.valueOf(containerColor));
            chip.setTextColor(onContainerColor);
            chip.setChipIconTint(ColorStateList.valueOf(onContainerColor));
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
        chip.setTextColor(onContainerColor);
        chip.setChipIconTint(ColorStateList.valueOf(onContainerColor));
        chip.setText(getString(R.string.deadline_prefix, formatted));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_task_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_save) {
            saveTask();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveTask() {
        if (currentTask == null) {
            return;
        }
        String title = binding.titleEdit.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            binding.titleEdit.setError(getString(R.string.new_task_hint));
            return;
        }
        String description = binding.descriptionEdit.getText().toString();
        currentTask.setTitle(title);
        currentTask.setDescription(description);
        currentTask.setDueAt(selectedDueAt);
        ioExecutor.execute(() -> taskDao.update(currentTask));
        Toast.makeText(this, R.string.save_task, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
}