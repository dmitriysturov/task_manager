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
import com.example.task_manager.databinding.FragmentTasksBinding;
import com.example.task_manager.model.Task;

import java.util.ArrayList;

public class TasksFragment extends Fragment implements TasksAdapter.OnTaskInteractionListener {

    private FragmentTasksBinding binding;
    private final ArrayList<Task> tasks = new ArrayList<>();
    private TasksAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        setupFab();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = binding.tasksList;
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TasksAdapter(tasks, this);
        recyclerView.setAdapter(adapter);
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
        Task task = new Task(title.trim());
        tasks.add(task);
        adapter.notifyItemInserted(tasks.size() - 1);
    }

    @Override
    public void onTaskChecked(int position, boolean isChecked) {
        if (position < 0 || position >= tasks.size()) {
            return;
        }
        tasks.get(position).setDone(isChecked);
        adapter.notifyItemChanged(position);
    }

    @Override
    public void onTaskLongPressed(int position) {
        if (position < 0 || position >= tasks.size()) {
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.delete_task_prompt)
                .setPositiveButton(R.string.delete, (dialog, which) -> removeTask(position))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void removeTask(int position) {
        tasks.remove(position);
        adapter.notifyItemRemoved(position);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}