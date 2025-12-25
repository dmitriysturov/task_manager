package com.example.task_manager.ui.common;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class UiStateViewModel extends ViewModel {

    public static final String MODE_WEEK = "WEEK";
    public static final String MODE_DAY = "DAY";

    private final MutableLiveData<Long> selectedGroupId = new MutableLiveData<>();
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>();
    private final MutableLiveData<String> calendarMode = new MutableLiveData<>();
    private final MutableLiveData<Integer> weekOffset = new MutableLiveData<>();
    private final MutableLiveData<Integer> dayOffset = new MutableLiveData<>();
    private final MutableLiveData<Long> selectedDayStart = new MutableLiveData<>();

    private boolean selectedGroupInitialized;
    private boolean searchQueryInitialized;
    private boolean calendarModeInitialized;
    private boolean weekOffsetInitialized;
    private boolean dayOffsetInitialized;
    private boolean selectedDayStartInitialized;

    public UiStateViewModel() {
        searchQuery.setValue("");
        calendarMode.setValue(MODE_WEEK);
        weekOffset.setValue(0);
        dayOffset.setValue(0);
    }

    public MutableLiveData<Long> getSelectedGroupId() {
        return selectedGroupId;
    }

    public void setSelectedGroupId(@Nullable Long groupId) {
        selectedGroupInitialized = true;
        selectedGroupId.setValue(groupId);
    }

    public boolean isSelectedGroupInitialized() {
        return selectedGroupInitialized;
    }

    public MutableLiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(@Nullable String query) {
        searchQueryInitialized = true;
        searchQuery.setValue(query == null ? "" : query);
    }

    public boolean isSearchQueryInitialized() {
        return searchQueryInitialized;
    }

    public MutableLiveData<String> getCalendarMode() {
        return calendarMode;
    }

    public void setCalendarMode(@Nullable String mode) {
        calendarModeInitialized = true;
        calendarMode.setValue(mode == null ? MODE_WEEK : mode);
    }

    public boolean isCalendarModeInitialized() {
        return calendarModeInitialized;
    }

    public MutableLiveData<Integer> getWeekOffset() {
        return weekOffset;
    }

    public void setWeekOffset(int offset) {
        weekOffsetInitialized = true;
        weekOffset.setValue(offset);
    }

    public boolean isWeekOffsetInitialized() {
        return weekOffsetInitialized;
    }

    public MutableLiveData<Integer> getDayOffset() {
        return dayOffset;
    }

    public void setDayOffset(int offset) {
        dayOffsetInitialized = true;
        dayOffset.setValue(offset);
    }

    public boolean isDayOffsetInitialized() {
        return dayOffsetInitialized;
    }

    public MutableLiveData<Long> getSelectedDayStart() {
        return selectedDayStart;
    }

    public void setSelectedDayStart(@Nullable Long dayStartMillis) {
        selectedDayStartInitialized = true;
        selectedDayStart.setValue(dayStartMillis);
    }

    public boolean isSelectedDayStartInitialized() {
        return selectedDayStartInitialized;
    }
}