package com.anannya.cicd;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    private final GreetingService service;

    public HomeController(GreetingService service) {
        this.service = service;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>CI/CD Demo</title>
                <style>
                  body{font-family:system-ui,sans-serif;background:#0f172a;color:#e2e8f0;
                       display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
                  .card{background:#1e293b;padding:2.5rem 3rem;border-radius:12px;text-align:center}
                  code{background:#0f172a;padding:.2rem .5rem;border-radius:6px;color:#38bdf8}
                </style></head>
                <body><div class="card">
                  <h1>%s</h1>
                  <p>Deployed automatically by the CI/CD pipeline</p>
                  <p>Version (git commit): <code>%s</code></p>
                </div></body></html>
                """.formatted(service.getMessage(), service.getVersion());
    }

    @GetMapping("/api/greet")
    public Map<String, String> greet(@RequestParam(required = false) String name) {
        return Map.of("greeting", service.buildGreeting(name));
    }

    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of("message", service.getMessage(), "version", service.getVersion());
    }
}
