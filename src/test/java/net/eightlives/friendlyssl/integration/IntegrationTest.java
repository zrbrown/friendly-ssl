package net.eightlives.friendlyssl.integration;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@Execution(ExecutionMode.SAME_THREAD)
@Tag("integration")
@Tag("slow")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public interface IntegrationTest extends ApplicationContextInitializer<ConfigurableApplicationContext> {

    Path TEMP = Path.of("src/test/resources/temp");

    @Override
    default void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
        GenericContainer<?> pebbleContainer = getPebbleContainer();
        pebbleContainer.start();

        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "friendly-ssl.acme-session-url=acme://pebble/localhost:" + pebbleContainer.getMappedPort(14000)
        );
    }

    GenericContainer getPebbleContainer();

    ScheduledExecutorService getTimer();

    @BeforeAll
    default void setUpAll() throws IOException {
        Files.createDirectories(TEMP);
    }

    @AfterAll
    default void tearDownAll() throws IOException {
        Files.walk(TEMP)
                .filter(path -> path != TEMP)
                .map(Path::toFile)
                .forEach(File::delete);
        Files.delete(TEMP);
    }

    @AfterEach
    default void tearDown() throws Exception {
        getTimer().shutdownNow();
    }

    default void testLogOutput(List<String> expectedLogs, CapturedOutput actualOutput) {
        while (!actualOutput.getOut().contains(expectedLogs.getLast())) {
            Thread.onSpinWait();
            if (Thread.interrupted()) {
                break;
            }
        }

        assertArrayEquals(
                expectedLogs.toArray(),
                actualOutput.getOut().lines()
                        .filter(line -> expectedLogs.stream().anyMatch(line::contains))
                        .map(line -> expectedLogs.stream().filter(line::contains).findFirst())
                        .map(Optional::get)
                        .distinct()
                        .toArray(),
                () -> "Actual output:\n" + actualOutput.getOut().lines()
                        .filter(line -> expectedLogs.stream().anyMatch(line::contains))
                        .map(line -> expectedLogs.stream().filter(line::contains).findFirst())
                        .map(Optional::get)
                        .collect(Collectors.joining("\n")) + "\n\n");
    }

    default void testLogOutputExact(List<String> expectedLogs, CapturedOutput actualOutput) {
        while (!actualOutput.getOut().contains(expectedLogs.getLast())) {
            Thread.onSpinWait();
            if (Thread.interrupted()) {
                break;
            }
        }

        assertArrayEquals(
                expectedLogs.toArray(),
                actualOutput.getOut().lines()
                        .filter(line -> expectedLogs.stream().anyMatch(line::contains))
                        .map(line -> expectedLogs.stream().filter(line::contains).findFirst())
                        .map(Optional::get)
                        .toArray(),
                () -> "Actual output:\n" + actualOutput.getOut().lines()
                        .filter(line -> expectedLogs.stream().anyMatch(line::contains))
                        .map(line -> expectedLogs.stream().filter(line::contains).findFirst())
                        .map(Optional::get)
                        .collect(Collectors.joining("\n")) + "\n\n");
    }
}
