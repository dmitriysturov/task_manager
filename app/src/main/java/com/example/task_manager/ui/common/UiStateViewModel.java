package com.example.task_manager.ui.common;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class UiStateViewModel extends ViewModel {

    public static final String MODE_WEEK = "WEEK";
    public static final String MODE_DAY = "DAY";
    public static final String GROUP_MODE_INBOX = "INBOX";
    public static final String GROUP_MODE_GROUP = "GROUP";
    public static final String GROUP_MODE_DEADLINES = "DEADLINES";

    private final MutableLiveData<Long> selectedGroupId = new MutableLiveData<>();
    private final MutableLiveData<String> selectedGroupMode = new MutableLiveData<>();
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>();
    private final MutableLiveData<String> searchTextQuery = new MutableLiveData<>();
    private final MutableLiveData<List<String>> searchTagNames = new MutableLiveData<>();
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
        searchTextQuery.setValue("");
        searchTagNames.setValue(new ArrayList<>());
        calendarMode.setValue(MODE_WEEK);
        weekOffset.setValue(0);
        dayOffset.setValue(0);
        selectedGroupMode.setValue(GROUP_MODE_INBOX);
    }

    public MutableLiveData<Long> getSelectedGroupId() {
        return selectedGroupId;
    }

    public void setSelectedGroupId(@Nullable Long groupId) {
        selectedGroupInitialized = true;
        selectedGroupId.setValue(groupId);
    }

    public MutableLiveData<String> getSelectedGroupMode() {
        return selectedGroupMode;
    }

    public void setSelectedGroupMode(@Nullable String mode) {
        selectedGroupInitialized = true;
        selectedGroupMode.setValue(mode == null ? GROUP_MODE_INBOX : mode);
    }

    public void setSelectedGroup(String mode, @Nullable Long groupId) {
        selectedGroupInitialized = true;
        selectedGroupId.setValue(groupId);
        selectedGroupMode.setValue(mode == null ? GROUP_MODE_INBOX : mode);
    }

    public boolean isSelectedGroupInitialized() {
        return selectedGroupInitialized;
    }

    public MutableLiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(@Nullable String query) {
        searchQueryInitialized = true;
        String normalized = query == null ? "" : query.trim();
        SearchQueryParts parts = parseSearchQuery(normalized);
        searchTextQuery.setValue(parts.textQuery);
        searchTagNames.setValue(parts.tagNames);
        searchQuery.setValue(normalized);
    }

    public boolean isSearchQueryInitialized() {
        return searchQueryInitialized;
    }

    public MutableLiveData<String> getSearchTextQuery() {
        return searchTextQuery;
    }

    public MutableLiveData<List<String>> getSearchTagNames() {
        return searchTagNames;
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

    private SearchQueryParts parseSearchQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return new SearchQueryParts("", new ArrayList<>());
        }
        String[] tokens = normalized.split("\\s+");
        List<String> textTokens = new ArrayList<>();
        Set<String> tags = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.startsWith("#")) {
                String tag = token.substring(1).trim().toLowerCase(Locale.ROOT);
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            } else {
                textTokens.add(token);
            }
        }
        String textQuery = buildTextQuery(textTokens);
        return new SearchQueryParts(textQuery, new ArrayList<>(tags));
    }

    private String buildTextQuery(List<String> textTokens) {
        if (textTokens == null || textTokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String token : textTokens) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(token);
        }
        return builder.toString().trim();
    }

    private static class SearchQueryParts {
        final String textQuery;
        final List<String> tagNames;

        SearchQueryParts(String textQuery, List<String> tagNames) {
            this.textQuery = textQuery == null ? "" : textQuery;
            this.tagNames = tagNames == null ? new ArrayList<>() : tagNames;
        }
    }
}