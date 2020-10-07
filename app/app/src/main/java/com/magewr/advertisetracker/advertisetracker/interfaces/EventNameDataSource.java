package com.magewr.advertisetracker.advertisetracker.interfaces;

import com.magewr.advertisetracker.advertisetracker.AdvertiseTracker;
import com.magewr.advertisetracker.advertisetracker.enums.ADType;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

// 이벤트명 제공받는 인터페이스
public interface EventNameDataSource {
    String getEventName(ADType type, int position, @Nullable RecyclerView.ViewHolder bannerViewHolder);
}