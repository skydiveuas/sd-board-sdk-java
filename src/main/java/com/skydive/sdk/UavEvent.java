package com.skydive.sdk;

/**
 * Created by Bartosz Nawrot on 2016-10-14.
 * This class contains events types that can be posted by UavManager.
 */
public class UavEvent {
    public enum Type {
        CONNECTED,
        DISCONNECTED,
        WARNING,
        ERROR,
        MESSAGE,
        CALIBRATION_NON_STATIC,
        DEBUG_UPDATED,
        AUTOPILOT_UPDATED,
        CALIBRATION_UPDATED,
        CONTROL_UPDATED,
        ROUTE_UPDATED,
        PING_UPDATED,
        CONTROL_UPLOADED,
        ROUTE_UPLOADED,
        FLIGHT_STARTED,
        FLIGHT_ENDED,
        MAGNETOMETER_CALIBRATION_STARTED,
        ACCEL_CALIB_DONE
    }

    private Type type;
    private String message;

    public UavEvent(Type type) {
        this.type = type;
    }

    public UavEvent(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
