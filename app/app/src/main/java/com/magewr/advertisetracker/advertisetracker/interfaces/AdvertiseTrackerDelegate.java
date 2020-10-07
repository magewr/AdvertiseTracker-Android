package com.magewr.advertisetracker.advertisetracker.interfaces;

// 애널리틱스 트래커 인터페이스 이 인터페이스를 구현한 애널리틱스 트래커는 모두 사용가능
public interface AdvertiseTrackerDelegate {
    void sendAdvertiseEvent(String advertiseName);
}