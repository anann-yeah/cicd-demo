package com.anannya.cicd;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void homePageReturnsHtmlWithMessage() throws Exception {
        mvc.perform(get("/"))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("CI/CD pipeline")));
    }

    @Test
    void greetEndpointUsesNameParameter() throws Exception {
        mvc.perform(get("/api/greet").param("name", "Anannya"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.greeting").value("Hello, Anannya!"));
    }

    @Test
    void greetEndpointDefaultsToWorld() throws Exception {
        mvc.perform(get("/api/greet"))
           .andExpect(jsonPath("$.greeting").value("Hello, World!"));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"));
    }
}
