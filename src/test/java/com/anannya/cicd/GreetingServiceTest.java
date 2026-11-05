package com.anannya.cicd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingServiceTest {

    private final GreetingService service = new GreetingService("Test message", "abc1234");

    @Test
    void greetsWorldWhenNameIsNull() {
        assertEquals("Hello, World!", service.buildGreeting(null));
    }

    @Test
    void greetsWorldWhenNameIsBlank() {
        assertEquals("Hello, World!", service.buildGreeting("   "));
    }

    @Test
    void greetsByNameAndTrimsWhitespace() {
        assertEquals("Hello, Anannya!", service.buildGreeting("  Anannya "));
    }

    @Test
    void exposesConfiguredMessageAndVersion() {
        assertEquals("Test message", service.getMessage());
        assertEquals("abc1234", service.getVersion());
    }
}
