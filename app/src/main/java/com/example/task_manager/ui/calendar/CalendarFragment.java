package com.example.task_manager.ui.calendar;

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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.task_manager.R;
import com.example.task_manager.data.AppDatabase;
import com.example.task_manager.data.TaskDao;
import com.example.task_manager.data.TaskWithGroup;
import com.example.task_manager.databinding.FragmentCalendarBinding;
import com.example.task_manager.ui.common.UiStateViewModel;
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

    private static final String PREFS_CALENDAR = "calendar_prefs";
    private static final String PREF_KEY_MODE = "calendar_mode";

    private FragmentCalendarBinding binding;
    private TaskDao taskDao;
    private ExecutorService ioExecutor;
    private DaySectionsAdapter adapter;
    private UiStateViewModel uiState;
    private LiveData<List<TaskWithGroup>> currentTasksLiveData;
    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dayTitleFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault());
    private Menu calendarMenu;
    private int weekOffset = 0;
    private int dayOffset = 0;


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

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        taskDao = AppDatabase.getInstance(requireContext()).taskDao();
        ioExecutor = Executors.newSingleThreadExecutor();
        uiState = new ViewModelProvider(requireActivity()).get(UiStateViewModel.class);
        initializeState(savedInstanceState);
        setupRecyclerView();
        setupMenu();
        applyWindowInsets();
        observeState();
    }

    private void setupRecyclerView() {
        binding.calendarList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DaySectionsAdapter(taskDao, ioExecutor, task -> startActivity(TaskDetailActivity.createIntent(requireContext(), task.getId())));
        binding.calendarList.setAdapter(adapter);
    }

    private void applyWindowInsets() {
        final int listPaddingStart = binding.calendarList.getPaddingStart();
        final int listPaddingTop = binding.calendarList.getPaddingTop();
        final int listPaddingEnd = binding.calendarList.getPaddingEnd();
        final int listPaddingBottom = binding.calendarList.getPaddingBottom();

        final int emptyPaddingStart = binding.calendarEmptyState.getPaddingStart();
        final int emptyPaddingTop = binding.calendarEmptyState.getPaddingTop();
        final int emptyPaddingEnd = binding.calendarEmptyState.getPaddingEnd();
        final int emptyPaddingBottom = binding.calendarEmptyState.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            binding.calendarList.setPaddingRelative(
                    listPaddingStart,
                    listPaddingTop,
                    listPaddingEnd,
                    listPaddingBottom + systemInsets.bottom
            );

            binding.calendarEmptyState.setPaddingRelative(
                    emptyPaddingStart,
                    emptyPaddingTop,
                    emptyPaddingEnd,
                    emptyPaddingBottom + systemInsets.bottom
            );

            return insets;
        });
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
                    if (isWeekMode()) {
                        uiState.setWeekOffset(getWeekOffset() - 1);
                    } else {
                        updateDayOffset(getDayOffset() - 1);
                    }
                    return true;
                } else if (id == R.id.action_next_range) {
                    if (isWeekMode()) {
                        uiState.setWeekOffset(getWeekOffset() + 1);
                    } else {
                        updateDayOffset(getDayOffset() + 1);
                    }
                    return true;
                } else if (id == R.id.action_today) {
                    uiState.setWeekOffset(0);
                    updateDayOffset(0);
                    return true;
                } else if (id == R.id.action_mode_week) {
                    switchMode(UiStateViewModel.MODE_WEEK);
                    if (calendarMenu != null) {
                        updateModeMenu(calendarMenu);
                    }
                    return true;
                } else if (id == R.id.action_mode_day) {
                    switchMode(UiStateViewModel.MODE_DAY);
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
            weekItem.setChecked(isWeekMode());
        }
        if (dayItem != null) {
            dayItem.setChecked(isDayMode());
        }
    }

    private void switchMode(String newMode) {
        if (newMode == null || newMode.equals(uiState.getCalendarMode().getValue())) {
            return;
        }
        if (UiStateViewModel.MODE_WEEK.equals(newMode)) {
            uiState.setSelectedDayStart(null);
        } else if (UiStateViewModel.MODE_DAY.equals(newMode)) {
            uiState.setDayOffset(0);
            if (uiState.getSelectedDayStart().getValue() == null) {
                uiState.setSelectedDayStart(startOfToday());
            }
        }
        uiState.setCalendarMode(newMode);
        saveMode(newMode);
    }

    private boolean isWeekMode() {
        return UiStateViewModel.MODE_WEEK.equals(uiState.getCalendarMode().getValue());
    }

    private boolean isDayMode() {
        return UiStateViewModel.MODE_DAY.equals(uiState.getCalendarMode().getValue());
    }

    private int getWeekOffset() {
        Integer value = uiState.getWeekOffset().getValue();
        return value == null ? 0 : value;
    }

    private int getDayOffset() {
        Integer value = uiState.getDayOffset().getValue();
        return value == null ? 0 : value;
    }

    private void updateDayOffset(int offset) {
        uiState.setDayOffset(offset);
        if (isDayMode()) {
            uiState.setSelectedDayStart(startOfDay(LocalDate.now().plusDays(offset)));
        }
    }

    private void renderWeek() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).plusWeeks(getWeekOffset());
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate startDay = getWeekOffset() == 0 ? today : weekStart;

        long from = startDay.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = weekEnd.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        observeRange(from, to, startDay, weekEnd, true);
        updateWeekTitle(weekStart, weekEnd);
        adapter.updateSelection(DaySectionsAdapter.SelectionMode.WEEK, today, null);
    }

    private void renderDay() {
        LocalDate today = LocalDate.now();
        LocalDate selectedDay = resolveSelectedDay(today);
        long from = selectedDay.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = selectedDay.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        observeRange(from, to, selectedDay, selectedDay, true);
        updateDayTitle(selectedDay);
        adapter.updateSelection(DaySectionsAdapter.SelectionMode.DAY, today, selectedDay);
    }

    private void observeRange(long from, long to, LocalDate startDay, LocalDate endDay, boolean includeEmptyDays) {
        if (currentTasksLiveData != null) {
            currentTasksLiveData.removeObservers(getViewLifecycleOwner());
        }
        currentTasksLiveData = taskDao.observeUndoneInRangeWithGroup(from, to, null, 0, getString(R.string.group_inbox));
        currentTasksLiveData.observe(getViewLifecycleOwner(), tasks -> buildSections(tasks, startDay, endDay, includeEmptyDays));
    }

    private void buildSections(@Nullable List<TaskWithGroup> tasks,
                               LocalDate startDay,
                               LocalDate endDay,
                               boolean includeEmptyDays) {

        Map<LocalDate, List<TaskWithGroup>> byDay = new HashMap<>();

        if (tasks != null) {
            for (TaskWithGroup taskWithGroup : tasks) {
                if (taskWithGroup.task == null || taskWithGroup.task.getDueAt() == null) {
                    continue;
                }

                LocalDate date = LocalDate.ofInstant(
                        java.time.Instant.ofEpochMilli(taskWithGroup.task.getDueAt()),
                        zoneId
                );

                List<TaskWithGroup> list = byDay.get(date);
                if (list == null) {
                    list = new ArrayList<>();
                    byDay.put(date, list);
                }
                list.add(taskWithGroup);
            }
        }

        List<DaySection> sections = new ArrayList<>();

        LocalDate cursor = startDay;
        while (!cursor.isAfter(endDay)) {

            List<TaskWithGroup> dayTasks = byDay.get(cursor);

            if (includeEmptyDays || (dayTasks != null && !dayTasks.isEmpty())) {
                sections.add(new DaySection(
                        cursor,
                        dayTasks == null ? new ArrayList<>() : dayTasks
                ));
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("week_offset", getWeekOffset());
        outState.putInt("day_offset", getDayOffset());
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
    private void initializeState(@Nullable Bundle savedInstanceState) {
        if (!uiState.isCalendarModeInitialized()) {
            uiState.setCalendarMode(restoreSavedMode());
        }
        if (!uiState.isWeekOffsetInitialized()) {
            int restored = savedInstanceState == null ? 0 : savedInstanceState.getInt("week_offset", 0);
            uiState.setWeekOffset(restored);
        }
        if (!uiState.isDayOffsetInitialized()) {
            int restored = savedInstanceState == null ? 0 : savedInstanceState.getInt("day_offset", 0);
            uiState.setDayOffset(restored);
        }
        if (isDayMode() && uiState.getSelectedDayStart().getValue() == null) {
            uiState.setSelectedDayStart(startOfDay(LocalDate.now().plusDays(getDayOffset())));
        }
    }

    private void observeState() {
        uiState.getCalendarMode().observe(getViewLifecycleOwner(), mode -> {
            if (calendarMenu != null) {
                updateModeMenu(calendarMenu);
            }
            render();
        });
        uiState.getWeekOffset().observe(getViewLifecycleOwner(), offset -> {
            if (isWeekMode()) {
                render();
            }
        });
        uiState.getDayOffset().observe(getViewLifecycleOwner(), offset -> {
            if (isDayMode()) {
                render();
            }
        });
        uiState.getSelectedDayStart().observe(getViewLifecycleOwner(), value -> {
            if (isDayMode()) {
                render();
            }
        });
    }

    private void render() {
        if (isWeekMode()) {
            renderWeek();
        } else {
            renderDay();
        }
    }

    private LocalDate resolveSelectedDay(LocalDate today) {
        Long selectedStart = uiState.getSelectedDayStart().getValue();
        if (selectedStart == null) {
            return today.plusDays(getDayOffset());
        }
        return LocalDate.ofInstant(java.time.Instant.ofEpochMilli(selectedStart), zoneId);
    }

    private long startOfToday() {
        return startOfDay(LocalDate.now());
    }

    private long startOfDay(LocalDate date) {
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli();
    }

    private void saveMode(String mode) {
        requireContext().getSharedPreferences(PREFS_CALENDAR, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_MODE, mode)
                .apply();
    }

    private String restoreSavedMode() {
        String stored = requireContext()
                .getSharedPreferences(PREFS_CALENDAR, android.content.Context.MODE_PRIVATE)
                .getString(PREF_KEY_MODE, UiStateViewModel.MODE_WEEK);
        if (UiStateViewModel.MODE_DAY.equals(stored) || UiStateViewModel.MODE_WEEK.equals(stored)) {
            return stored;
        }
        return UiStateViewModel.MODE_WEEK;
    }
}