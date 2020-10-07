package com.magewr.advertisetracker.base;

import android.os.Bundle;

import com.magewr.advertisetracker.advertisetracker.AdvertiseTracker;
import com.magewr.advertisetracker.advertisetracker.interfaces.AdvertiseTrackerDataSource;
import com.magewr.advertisetracker.advertisetracker.interfaces.AdvertiseTrackerDelegate;
import com.magewr.advertisetracker.analytics.DummyAnalytics;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class BaseActivity extends AppCompatActivity implements AdvertiseTrackerDataSource {

    protected DummyAnalytics eventTracker;
    protected AdvertiseTracker advertiseTracker;
    protected CompositeDisposable disposeBag = new CompositeDisposable();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        eventTracker = new DummyAnalytics();
        advertiseTracker = new AdvertiseTracker();
    }

    @Override
    public AdvertiseTrackerDelegate getAdvertiseTracker() {
        return eventTracker;
    }

    @Override
    protected void onDestroy() {
        eventTracker = null;
        advertiseTracker = null;
        if (!disposeBag.isDisposed())
            disposeBag.dispose();
        super.onDestroy();
    }
}
