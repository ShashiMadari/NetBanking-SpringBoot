package com.inetbank.identity.notification;

public enum NotificationPeriod {

    ALL(0),
    LAST_5_DAYS(5),
    LAST_10_DAYS(10);

    private final int days;

    NotificationPeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}