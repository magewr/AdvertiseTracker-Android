package com.magewr.advertisetracker.main;

import android.os.Bundle;
import android.view.View;

import com.magewr.advertisetracker.R;
import com.magewr.advertisetracker.advertisetracker.enums.ADType;
import com.magewr.advertisetracker.advertisetracker.interfaces.EventNameDataSource;
import com.magewr.advertisetracker.base.BaseActivity;
import com.magewr.advertisetracker.databinding.ActivityMainBinding;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends BaseActivity implements EventNameDataSource {

    private ActivityMainBinding bnd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bnd = DataBindingUtil.setContentView(this, R.layout.activity_main);

        disposeBag.add(advertiseTracker.addAdTrackingTypeListInScroll(ADType.HomeBigBanner, bnd.recyclerView, DummyViewHolder.class));
        disposeBag.add(advertiseTracker.addAdTrackingPagerInList(ADType.HomeSmallBanner, bnd.recyclerView, DummyViewHolder2.class));
    }

    @Override
    public String getEventName(ADType type, int position, @Nullable RecyclerView.ViewHolder bannerViewHolder) {
        switch (type) {
            case HomeBigBanner:
                if (bannerViewHolder instanceof DummyViewHolder)
                    return ((DummyViewHolder) bannerViewHolder).title;
                break;
            case HomeSmallBanner:
                if (bannerViewHolder instanceof DummyViewHolder2)
                    return ((DummyViewHolder2) bannerViewHolder).id;
                break;
        }

        return "Unknown";
    }
}

class DummyViewHolder extends RecyclerView.ViewHolder {

    public String title = "Sample";

    public DummyViewHolder(@NonNull View itemView) {
        super(itemView);
    }
}

class DummyViewHolder2 extends RecyclerView.ViewHolder {

    public String id = "101";

    public DummyViewHolder2(@NonNull View itemView) {
        super(itemView);
    }
}