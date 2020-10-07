package com.magewr.advertisetracker.advertisetracker.enums;



// 광고 타입
public enum ADType {
    HomeBigBanner("홈_메인배너"),
    HomeSmallBanner("홈_띠배너");

    private String typeName;

    ADType(String typeName) {
        this.typeName = typeName;
    }

    /**
     * PV, UV 등 FA View Event 전송용 스트링
     * @param eventName 이벤트명
     * @param isUnique 유니크 여부
     * @return 스트링
     */
    public String getViewEventFullString(String eventName, boolean isUnique) {
        String eventString;
        eventString = String.format("%s_%s_%s", this.typeName, isUnique ? "SV" : "PV", eventName);

        return eventString.replace(" ", "_");
    }

    /**
     * PC, UC 등 FA Click Event 전송용 스트링
     * @param eventName 이벤트명
     * @param isUnique 유니크여부
     * @return 스트링
     */
    public String getClickEventFullString(String eventName, boolean isUnique) {
        String eventString;
        eventString = String.format("%s_%s_%s", this.typeName, isUnique ? "SC" : "C", eventName);

        return eventString.replace(" ", "_");
    }
}