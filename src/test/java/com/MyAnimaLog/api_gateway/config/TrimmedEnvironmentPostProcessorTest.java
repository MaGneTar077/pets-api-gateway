package com.MyAnimaLog.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class TrimmedEnvironmentPostProcessorTest {

    private final TrimmedEnvironmentPostProcessor processor = new TrimmedEnvironmentPostProcessor();

    @Test
    void trimsTrailingAndLeadingSpaces() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("CALENDAR_SERVICE_URL", " https://calendar-service.run.app ");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("CALENDAR_SERVICE_URL")).isEqualTo("https://calendar-service.run.app");
    }

    @Test
    void leavesAlreadyCleanValueUnchanged() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("CALENDAR_SERVICE_URL", "https://calendar-service.run.app");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("CALENDAR_SERVICE_URL")).isEqualTo("https://calendar-service.run.app");
    }

    @Test
    void doesNothingWhenPropertyIsAbsent() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("CALENDAR_SERVICE_URL")).isNull();
    }
}
