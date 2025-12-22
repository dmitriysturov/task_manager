package com.example.task_manager.ui.groups;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.task_manager.R;
import com.example.task_manager.data.AppDatabase;
import com.example.task_manager.data.GroupDao;
import com.example.task_manager.data.GroupEntity;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.databinding.ActivityGroupsBinding;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GroupsActivity extends AppCompatActivity {

    public static Intent createIntent(Context context) {
        return new Intent(context, GroupsActivity.class);
    }

    private ActivityGroupsBinding binding;
    private GroupDao groupDao;
    private TaskDao taskDao;
    private ExecutorService ioExecutor;
    private GroupsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGroupsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        AppDatabase db = AppDatabase.getInstance(this);
        groupDao = db.groupDao();
        taskDao = db.taskDao();
        ioExecutor = Executors.newSingleThreadExecutor();

        setupRecyclerView();
        observeGroups();
        binding.addGroupFab.setOnClickListener(v -> showAddGroupDialog());
    }

    private void setupRecyclerView() {
        adapter = new GroupsAdapter(this::showRenameDialog, this::confirmDeleteGroup);
        binding.groupsList.setLayoutManager(new LinearLayoutManager(this));
        binding.groupsList.setAdapter(adapter);
    }

    private void observeGroups() {
        groupDao.observeAllOrdered().observe(this, groups -> {
            adapter.submitList(groups);
            boolean isEmpty = groups == null || groups.isEmpty();
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.groupsList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
    }

    private void showAddGroupDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_group, null, false);
        EditText input = dialogView.findViewById(R.id.input_group_name);

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_group)
                .setView(dialogView)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        return;
                    }
                    GroupEntity group = new GroupEntity(name);
                    group.setOrderIndex(adapter.getItemCount());
                    ioExecutor.execute(() -> groupDao.insert(group));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRenameDialog(GroupEntity group) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_group, null, false);
        EditText input = dialogView.findViewById(R.id.input_group_name);
        input.setText(group.getName());

        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_group)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        return;
                    }
                    group.setName(name);
                    ioExecutor.execute(() -> groupDao.update(group));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteGroup(GroupEntity group) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_group_prompt)
                .setPositiveButton(R.string.delete, (dialog, which) -> ioExecutor.execute(() -> {
                    taskDao.clearGroupId(group.getId());
                    groupDao.delete(group);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
}