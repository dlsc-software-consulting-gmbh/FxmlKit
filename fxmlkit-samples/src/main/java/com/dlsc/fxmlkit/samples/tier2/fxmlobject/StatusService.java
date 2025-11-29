package com.dlsc.fxmlkit.samples.tier2.fxmlobject;

import java.time.LocalTime;

/**
 * Service that provides system status information.
 */
public class StatusService {

    public String getStatus() {
        return "Online";
    }

    public String getVersion() {
        return "1.0.0";
    }

    public String getServerTime() {
        return LocalTime.now().toString().substring(0, 8);
    }
}