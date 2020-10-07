package com.magewr.advertisetracker.analytics;

import com.magewr.advertisetracker.advertisetracker.interfaces.AdvertiseTrackerDelegate;

public class DummyAnalytics implements AdvertiseTrackerDelegate {

    @Override
    public void sendAdvertiseEvent(String advertiseName) {
        // tracker.sendEvent(advertiseName);
    }
}
