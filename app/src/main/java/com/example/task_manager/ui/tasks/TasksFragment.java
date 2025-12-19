package com.example.task_manager.ui.tasks;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.data.AppDatabase;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.databinding.FragmentTasksBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TasksFragment extends Fragment {

    private FragmentTasksBinding binding;
    private TasksAdapter adapter;
    private TaskDao taskDao;
    private ExecutorService ioExecutor;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        taskDao = AppDatabase.getInstance(requireContext()).taskDao();
        ioExecutor = Executors.newSingleThreadExecutor();
        setupRecyclerView();
        observeTasks();
        setupFab();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = binding.tasksList;
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TasksAdapter(taskDao, ioExecutor, this::onTaskLongPressed, this::onTaskClicked);
        recyclerView.setAdapter(adapter);
    }

    private void observeTasks() {
        taskDao.observeAll().observe(getViewLifecycleOwner(), adapter::submitList);
    }

    private void setupFab() {
        View fab = binding.fab;
        fab.setOnClickListener(v -> showTaskDialog(null));
    }

    private void showTaskDialog(@Nullable TaskEntity existingTask) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_task, null, false);
        EditText input = dialogView.findViewById(R.id.input_title);
        TextView deadlineText = dialogView.findViewById(R.id.deadline_text);
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

    private void openDeadlinePicker(Long[] selectedDueAt, TextView deadlineText) {
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

    private void updateDeadlineText(TextView textView, @Nullable Long dueAt) {
        if (dueAt == null) {
            textView.setText(R.string.deadline_not_set);
        } else {
            String formatted = dateFormat.format(dueAt);
            textView.setText(getString(R.string.deadline_prefix, formatted));
        }
    }

    private void onTaskLongPressed(TaskEntity task) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.delete_task_prompt)
                .setPositiveButton(R.string.delete, (dialog, which) -> removeTask(task))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void onTaskClicked(TaskEntity task) {
        showTaskDialog(task);
    }

    private void removeTask(TaskEntity task) {
        ioExecutor.execute(() -> taskDao.delete(task));
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
}