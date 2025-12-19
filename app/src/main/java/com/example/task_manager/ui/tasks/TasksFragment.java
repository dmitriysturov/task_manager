package com.example.task_manager.ui.tasks;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TasksFragment extends Fragment {

    private FragmentTasksBinding binding;
    private TasksAdapter adapter;
    private TaskDao taskDao;
    private ExecutorService ioExecutor;

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
        adapter = new TasksAdapter(taskDao, ioExecutor, this::onTaskLongPressed);
        recyclerView.setAdapter(adapter);
    }

    private void observeTasks() {
        taskDao.observeAll().observe(getViewLifecycleOwner(), adapter::submitList);
    }

    private void setupFab() {
        View fab = binding.fab;
        fab.setOnClickListener(v -> showAddTaskDialog());
    }

    private void showAddTaskDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.new_task_hint));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_task_title)
                .setView(input)
                .setPositiveButton(R.string.add, (dialog, which) -> addTask(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addTask(String title) {
        if (TextUtils.isEmpty(title.trim())) {
            return;
        }
        TaskEntity task = new TaskEntity(title.trim(), false);
        ioExecutor.execute(() -> taskDao.insert(task));
    }

    private void onTaskLongPressed(TaskEntity task) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.delete_task_prompt)
                .setPositiveButton(R.string.delete, (dialog, which) -> removeTask(task))
                .setNegativeButton(R.string.cancel, null)
                .show();
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