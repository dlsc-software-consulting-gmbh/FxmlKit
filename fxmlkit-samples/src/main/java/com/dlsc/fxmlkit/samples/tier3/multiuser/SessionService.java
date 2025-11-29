package com.dlsc.fxmlkit.samples.tier3.multiuser;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Session-scoped service that holds user-specific data.
 *
 * <p>In a real JPro application, each browser session would have
 * its own instance of this service, containing user-specific state.
 *
 * <p>This demo simulates that by creating separate instances
 * for each Tab (representing different users).
 */
public class SessionService {

    private final String username;
    private final String sessionId;
    private final String loginTime;
    private int actionCount = 0;

    public SessionService(String username) {
        this.username = username;
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.loginTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public String getUsername() {
        return username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getLoginTime() {
        return loginTime;
    }

    public int getActionCount() {
        return actionCount;
    }

    public void incrementAction() {
        actionCount++;
    }

    public String getWelcomeMessage() {
        return "Welcome, " + username + "!";
    }
}