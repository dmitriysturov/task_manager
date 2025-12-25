package com.example.task_manager.ui.archive;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.task_manager.R;
import com.example.task_manager.data.AppDatabase;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.data.TaskWithGroup;
import com.example.task_manager.databinding.FragmentArchiveBinding;
import com.example.task_manager.ui.calendar.DaySection;
import com.example.task_manager.ui.calendar.DaySectionsAdapter;
import com.example.task_manager.ui.taskdetail.TaskDetailActivity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArchiveFragment extends Fragment {

    private enum Filter {
        DONE_ONLY,
        EVERYTHING_PAST
    }

    private FragmentArchiveBinding binding;
    private TaskDao taskDao;
    private ExecutorService ioExecutor;
    private DaySectionsAdapter adapter;
    private Filter filter = Filter.DONE_ONLY;
    private LiveData<List<TaskWithGroup>> doneLiveData;
    private LiveData<List<TaskWithGroup>> pastUndoneLiveData;
    private final List<TaskWithGroup> doneTasksCache = new ArrayList<>();
    private final List<TaskWithGroup> pastUndoneCache = new ArrayList<>();
    private final ZoneId zoneId = ZoneId.systemDefault();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentArchiveBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().setTitle(R.string.archive_title);
        taskDao = AppDatabase.getInstance(requireContext()).taskDao();
        ioExecutor = Executors.newSingleThreadExecutor();
        setupRecyclerView();
        setupToggle();
        observeData();
        applyWindowInsets();
    }

    private void setupRecyclerView() {
        binding.archiveList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DaySectionsAdapter(taskDao, ioExecutor, task -> startActivity(TaskDetailActivity.createIntent(requireContext(), task.getId())));
        binding.archiveList.setAdapter(adapter);
    }

    private void applyWindowInsets() {
        final int listPaddingStart = binding.archiveList.getPaddingStart();
        final int listPaddingTop = binding.archiveList.getPaddingTop();
        final int listPaddingEnd = binding.archiveList.getPaddingEnd();
        final int listPaddingBottom = binding.archiveList.getPaddingBottom();

        final int emptyPaddingStart = binding.archiveEmptyState.getPaddingStart();
        final int emptyPaddingTop = binding.archiveEmptyState.getPaddingTop();
        final int emptyPaddingEnd = binding.archiveEmptyState.getPaddingEnd();
        final int emptyPaddingBottom = binding.archiveEmptyState.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            binding.archiveList.setPaddingRelative(
                    listPaddingStart,
                    listPaddingTop,
                    listPaddingEnd,
                    listPaddingBottom + systemInsets.bottom
            );

            binding.archiveEmptyState.setPaddingRelative(
                    emptyPaddingStart,
                    emptyPaddingTop,
                    emptyPaddingEnd,
                    emptyPaddingBottom + systemInsets.bottom
            );

            return insets;
        });
    }

    private void setupToggle() {
        binding.archiveFilterGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.filter_done_only) {
                filter = Filter.DONE_ONLY;
            } else if (checkedId == R.id.filter_all_past) {
                filter = Filter.EVERYTHING_PAST;
            }
            rebuildSections();
        });
        binding.archiveFilterGroup.check(R.id.filter_done_only);
    }

    private void observeData() {
        long startOfToday = LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli();
        doneLiveData = taskDao.observeDoneAllWithGroup(getString(R.string.group_inbox));
        pastUndoneLiveData = taskDao.observeUndoneInRangeWithGroup(0, startOfToday - 1, null, 0, getString(R.string.group_inbox));

        doneLiveData.observe(getViewLifecycleOwner(), tasks -> {
            doneTasksCache.clear();
            if (tasks != null) {
                doneTasksCache.addAll(tasks);
            }
            rebuildSections();
        });

        pastUndoneLiveData.observe(getViewLifecycleOwner(), tasks -> {
            pastUndoneCache.clear();
            if (tasks != null) {
                pastUndoneCache.addAll(tasks);
            }
            rebuildSections();
        });
    }

    private void rebuildSections() {
        List<TaskWithGroup> tasksForDisplay = new ArrayList<>();
        long startOfToday = LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli();
        if (filter == Filter.DONE_ONLY) {
            tasksForDisplay.addAll(doneTasksCache);
        } else {
            for (TaskWithGroup done : doneTasksCache) {
                TaskEntity task = done.task;
                if (task == null) {
                    continue;
                }
                long ts = task.getDueAt() != null ? task.getDueAt() : task.getCreatedAt();
                if (ts < startOfToday) {
                    tasksForDisplay.add(done);
                }
            }
            tasksForDisplay.addAll(pastUndoneCache);
        }

        tasksForDisplay.sort((a, b) -> Long.compare(getTaskTime(b.task), getTaskTime(a.task)));

        Map<LocalDate, List<TaskWithGroup>> grouped = new HashMap<>();
        for (TaskWithGroup taskWithGroup : tasksForDisplay) {
            if (taskWithGroup.task == null) {
                continue;
            }
            LocalDate day = LocalDate.ofInstant(Instant.ofEpochMilli(getTaskTime(taskWithGroup.task)), zoneId);
            List<TaskWithGroup> bucket = grouped.get(day);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(day, bucket);
            }
            bucket.add(taskWithGroup);
        }

        List<LocalDate> days = new ArrayList<>(grouped.keySet());
        days.sort(Comparator.reverseOrder());

        List<DaySection> sections = new ArrayList<>();
        for (LocalDate day : days) {
            sections.add(new DaySection(day, grouped.get(day)));
        }

        adapter.submitList(sections);
        boolean isEmpty = tasksForDisplay.isEmpty();
        binding.archiveEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.archiveList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private long getTaskTime(TaskEntity task) {
        if (task == null) {
            return 0;
        }
        return task.getDueAt() != null ? task.getDueAt() : task.getCreatedAt();
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