package com.example.task_manager.ui.groups;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.task_manager.R;
import com.example.task_manager.data.GroupEntity;

public class GroupsAdapter extends ListAdapter<GroupEntity, GroupsAdapter.GroupViewHolder> {

    public interface OnGroupClickListener {
        void onGroupClick(@NonNull GroupEntity group);
    }

    public interface OnGroupLongClickListener {
        void onGroupLongClick(@NonNull GroupEntity group);
    }

    private final OnGroupClickListener clickListener;
    private final OnGroupLongClickListener longClickListener;

    public GroupsAdapter(OnGroupClickListener clickListener, OnGroupLongClickListener longClickListener) {
        super(new DiffUtil.ItemCallback<GroupEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull GroupEntity oldItem, @NonNull GroupEntity newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull GroupEntity oldItem, @NonNull GroupEntity newItem) {
                return oldItem.getName().equals(newItem.getName())
                        && oldItem.getColor() == newItem.getColor()
                        && oldItem.getOrderIndex() == newItem.getOrderIndex();
            }
        });
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        GroupEntity item = getItem(position);
        return item != null ? item.getId() : RecyclerView.NO_ID;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        GroupEntity item = getItem(position);
        if (item == null) {
            return;
        }
        holder.name.setText(item.getName());
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onGroupClick(item);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onGroupLongClick(item);
            }
            return true;
        });
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        final TextView name;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.group_name);
        }
    }
}