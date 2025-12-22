package com.example.task_manager.ui.taskdetail;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.task_manager.R;
import com.example.task_manager.data.AppDatabase;
import com.example.task_manager.data.SubtaskDao;
import com.example.task_manager.data.SubtaskEntity;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.databinding.ActivityTaskDetailBinding;
import com.example.task_manager.ui.tasks.SubtaskMiniAdapter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
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
    private ExecutorService ioExecutor;
    private long taskId;
    @Nullable
    private Long selectedDueAt;
    private TaskEntity currentTask;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    private SubtaskMiniAdapter subtaskAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskId = getIntent().getLongExtra(EXTRA_TASK_ID, -1);
        if (taskId == -1) {
            finish();
            return;
        }
        binding = ActivityTaskDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        taskDao = AppDatabase.getInstance(this).taskDao();
        subtaskDao = AppDatabase.getInstance(this).subtaskDao();
        ioExecutor = Executors.newSingleThreadExecutor();

        setupDeadlineButtons();
        setupSubtasks();
        observeTask();
        observeSubtasks();
    }

    private void setupDeadlineButtons() {
        binding.selectDeadlineButton.setOnClickListener(v -> openDeadlinePicker());
        binding.resetDeadlineButton.setOnClickListener(v -> {
            selectedDueAt = null;
            updateDeadlineText(binding.deadlineText, null);
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
        binding.addSubtaskButton.setOnClickListener(v -> showAddSubtaskDialog());
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
            updateDeadlineText(binding.deadlineText, selectedDueAt);
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
                updateDeadlineText(binding.deadlineText, selectedDueAt);
            }, hour, minute, DateFormat.is24HourFormat(this));

            timePickerDialog.show();
        }, year, month, day);

        datePickerDialog.show();
    }

    private void updateDeadlineText(TextView textView, @Nullable Long dueAt) {
        if (dueAt == null) {
            textView.setText(R.string.deadline_not_set);
        } else {
            String formatted = dateFormat.format(dueAt);
            textView.setText(getString(R.string.deadline_prefix, formatted));
        }
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