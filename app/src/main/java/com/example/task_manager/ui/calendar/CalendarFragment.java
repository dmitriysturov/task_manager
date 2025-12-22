package com.example.task_manager.ui.calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.task_manager.R;
import com.example.task_manager.data.AppDatabase;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskEntity;
import com.example.task_manager.databinding.FragmentCalendarBinding;
import com.example.task_manager.ui.taskdetail.TaskDetailActivity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalendarFragment extends Fragment {

    private enum Mode {
        WEEK,
        DAY
    }

    private FragmentCalendarBinding binding;
    private TaskDao taskDao;
    private ExecutorService ioExecutor;
    private DaySectionsAdapter adapter;
    private Mode mode = Mode.WEEK;
    private int weekOffset = 0;
    private int dayOffset = 0;
    private LiveData<List<TaskEntity>> currentTasksLiveData;
    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dayTitleFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault());
    private final String prefKey = "calendar_mode";
    private Menu calendarMenu;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreSavedMode();
        if (savedInstanceState != null) {
            weekOffset = savedInstanceState.getInt("week_offset", 0);
            dayOffset = savedInstanceState.getInt("day_offset", 0);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        taskDao = AppDatabase.getInstance(requireContext()).taskDao();
        ioExecutor = Executors.newSingleThreadExecutor();
        setupRecyclerView();
        setupMenu();
        render();
    }

    private void setupRecyclerView() {
        binding.calendarList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DaySectionsAdapter(taskDao, ioExecutor, task -> startActivity(TaskDetailActivity.createIntent(requireContext(), task.getId())));
        binding.calendarList.setAdapter(adapter);
    }

    private void setupMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_calendar, menu);
                calendarMenu = menu;
                updateModeMenu(menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_prev_range) {
                    if (mode == Mode.WEEK) {
                        weekOffset--;
                    } else {
                        dayOffset--;
                    }
                    render();
                    return true;
                } else if (id == R.id.action_next_range) {
                    if (mode == Mode.WEEK) {
                        weekOffset++;
                    } else {
                        dayOffset++;
                    }
                    render();
                    return true;
                } else if (id == R.id.action_today) {
                    weekOffset = 0;
                    dayOffset = 0;
                    render();
                    return true;
                } else if (id == R.id.action_mode_week) {
                    switchMode(Mode.WEEK);
                    if (calendarMenu != null) {
                        updateModeMenu(calendarMenu);
                    }
                    return true;
                } else if (id == R.id.action_mode_day) {
                    switchMode(Mode.DAY);
                    if (calendarMenu != null) {
                        updateModeMenu(calendarMenu);
                    }
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void updateModeMenu(@NonNull Menu menu) {
        MenuItem weekItem = menu.findItem(R.id.action_mode_week);
        MenuItem dayItem = menu.findItem(R.id.action_mode_day);
        if (weekItem != null) {
            weekItem.setChecked(mode == Mode.WEEK);
        }
        if (dayItem != null) {
            dayItem.setChecked(mode == Mode.DAY);
        }
    }

    private void switchMode(Mode newMode) {
        if (mode == newMode) {
            return;
        }
        mode = newMode;
        if (mode == Mode.DAY) {
            dayOffset = 0;
        }
        saveMode();
        render();
    }

    private void render() {
        if (mode == Mode.WEEK) {
            renderWeek();
        } else {
            renderDay();
        }
    }

    private void renderWeek() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate startDay = weekOffset == 0 ? today : weekStart;

        long from = startDay.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = weekEnd.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        observeRange(from, to, startDay, weekEnd, true);
        updateWeekTitle(weekStart, weekEnd);
    }

    private void renderDay() {
        LocalDate today = LocalDate.now();
        LocalDate selectedDay = today.plusDays(dayOffset);
        long from = selectedDay.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = selectedDay.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        observeRange(from, to, selectedDay, selectedDay, true);
        updateDayTitle(selectedDay);
    }

    private void observeRange(long from, long to, LocalDate startDay, LocalDate endDay, boolean includeEmptyDays) {
        if (currentTasksLiveData != null) {
            currentTasksLiveData.removeObservers(getViewLifecycleOwner());
        }
        currentTasksLiveData = taskDao.observeUndoneInRange(from, to);
        currentTasksLiveData.observe(getViewLifecycleOwner(), tasks -> buildSections(tasks, startDay, endDay, includeEmptyDays));
    }

    private void buildSections(@Nullable List<TaskEntity> tasks, LocalDate startDay, LocalDate endDay, boolean includeEmptyDays) {
        Map<LocalDate, List<TaskEntity>> byDay = new HashMap<>();
        if (tasks != null) {
            for (TaskEntity task : tasks) {
                if (task.getDueAt() == null) {
                    continue;
                }
                LocalDate date = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(task.getDueAt()), zoneId);
                List<TaskEntity> list = byDay.get(date);
                if (list == null) {
                    list = new ArrayList<>();
                    byDay.put(date, list);
                }
                list.add(task);
            }
        }

        List<DaySection> sections = new ArrayList<>();
        LocalDate cursor = startDay;
        while (!cursor.isAfter(endDay)) {
            List<TaskEntity> dayTasks = byDay.get(cursor);
            if (includeEmptyDays || (dayTasks != null && !dayTasks.isEmpty())) {
                sections.add(new DaySection(cursor, dayTasks == null ? new ArrayList<>() : dayTasks));
            }
            cursor = cursor.plusDays(1);
        }

        adapter.submitList(sections);
        boolean hasTasks = tasks != null && !tasks.isEmpty();
        binding.calendarEmptyState.setVisibility(hasTasks ? View.GONE : View.VISIBLE);
    }

    private void updateWeekTitle(LocalDate weekStart, LocalDate weekEnd) {
        String start = dayTitleFormatter.format(weekStart);
        String end = dayTitleFormatter.format(weekEnd);
        requireActivity().setTitle(getString(R.string.calendar_week_title, start, end));
    }

    private void updateDayTitle(LocalDate day) {
        String formatted = dayTitleFormatter.format(day);
        requireActivity().setTitle(getString(R.string.calendar_day_title, formatted));
    }

    private void saveMode() {
        SharedPreferences prefs = requireContext().getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString(prefKey, mode.name()).apply();
    }

    private void restoreSavedMode() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE);
        String stored = prefs.getString(prefKey, Mode.WEEK.name());
        try {
            mode = Mode.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            mode = Mode.WEEK;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("week_offset", weekOffset);
        outState.putInt("day_offset", dayOffset);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (currentTasksLiveData != null) {
            currentTasksLiveData.removeObservers(getViewLifecycleOwner());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
}