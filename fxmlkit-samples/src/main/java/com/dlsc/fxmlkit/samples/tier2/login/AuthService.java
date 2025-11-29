package com.dlsc.fxmlkit.samples.tier2.login;

/**
 * Simple authentication service for demonstration.
 *
 * <p>In a real application, this would connect to a backend service.
 * Here it just simulates authentication with hardcoded credentials.
 *
 * <p>This service is injected into {@link LoginController} via FxmlKit's
 * dependency injection mechanism.
 */
public class AuthService {

    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "pswd";

    /**
     * Authenticates a user with the given credentials.
     *
     * @param username the username
     * @param password the password
     * @return true if authentication succeeds, false otherwise
     */
    public boolean authenticate(String username, String password) {
        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return VALID_USERNAME.equals(username) && VALID_PASSWORD.equals(password);
    }

    /**
     * Returns a welcome message for the authenticated user.
     *
     * @param username the username
     * @return welcome message
     */
    public String getWelcomeMessage(String username) {
        return "Welcome back, " + username + "!";
    }
}