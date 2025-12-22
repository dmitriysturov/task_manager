package com.example.task_manager.ui.tasks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.data.SubtaskEntity;

import java.util.ArrayList;
import java.util.List;

public class SubtaskMiniAdapter extends RecyclerView.Adapter<SubtaskMiniAdapter.SubtaskViewHolder> {

    public interface OnToggleListener {
        void onToggle(SubtaskEntity subtask);
    }

    public interface OnDeleteListener {
        void onDelete(SubtaskEntity subtask);
    }

    private final List<SubtaskEntity> items = new ArrayList<>();
    private final OnToggleListener toggleListener;
    private final OnDeleteListener deleteListener;

    public SubtaskMiniAdapter(OnToggleListener toggleListener, OnDeleteListener deleteListener) {
        this.toggleListener = toggleListener;
        this.deleteListener = deleteListener;
    }

    public void submitList(List<SubtaskEntity> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SubtaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subtask_mini, parent, false);
        return new SubtaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubtaskViewHolder holder, int position) {
        SubtaskEntity subtask = items.get(position);
        holder.title.setText(subtask.title);
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(subtask.done);
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (toggleListener != null) {
                toggleListener.onToggle(subtask);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(subtask);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SubtaskViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final TextView title;

        SubtaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.subtask_checkbox);
            title = itemView.findViewById(R.id.subtask_title);
        }
    }
}