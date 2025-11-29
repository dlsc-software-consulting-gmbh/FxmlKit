package com.dlsc.fxmlkit.samples.tier2.guice;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TimeService {

    public String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}