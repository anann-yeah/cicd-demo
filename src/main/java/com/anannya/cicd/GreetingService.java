package com.anannya.cicd;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Business logic kept separate from the web layer so it is easy to unit-test. */
@Service
public class GreetingService {

    private final String message;
    private final String version;

    public GreetingService(@Value("${app.message}") String message,
                           @Value("${APP_VERSION:dev}") String version) {
        this.message = message;
        this.version = version;
    }

    public String getMessage() {
        return message;
    }

    /** Git commit SHA baked into the image at build time (or "dev" locally). */
    public String getVersion() {
        return version;
    }

    public String buildGreeting(String name) {
        if (name == null || name.isBlank()) {
            return "Hello, World!";
        }
        return "Hello, " + name.trim() + "!";
    }
}
