package com.mtg.collection.service.health;

import com.mtg.collection.repository.HealthDailyMetricRepository;
import com.mtg.collection.repository.HealthMetricRepository;
import com.mtg.collection.repository.HealthWorkoutRepository;
import com.mtg.collection.service.HealthImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HealthImportService} status management.
 *
 * <p>Tests exercise the public status-machine API and the guard against concurrent
 * imports.  The actual SAX-parsing path requires MongoDB and is tested manually.</p>
 */
@ExtendWith(MockitoExtension.class)
class HealthImportServiceTest {

    @Mock private HealthMetricRepository metricRepo;
    @Mock private HealthDailyMetricRepository dailyRepo;
    @Mock private HealthWorkoutRepository workoutRepo;
    @Mock private MongoTemplate mongoTemplate;

    private HealthImportService service;

    @BeforeEach
    void setUp() {
        service = new HealthImportService(metricRepo, dailyRepo, workoutRepo, mongoTemplate);
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void initialStatus_isIdle() {
        assertThat(service.getStatus()).isEqualTo("idle");
    }

    @Test
    void initialRunning_isFalse() {
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void initialError_isNull() {
        assertThat(service.getImportError()).isNull();
    }

    @Test
    void initialCounters_areZero() {
        assertThat(service.getProcessedRecords()).isEqualTo(0L);
        assertThat(service.getImportedMetrics()).isEqualTo(0L);
        assertThat(service.getImportedWorkouts()).isEqualTo(0L);
        assertThat(service.getImportedDays()).isEqualTo(0L);
    }

    // ── Error-path via missing file ───────────────────────────────────────────

    @Test
    void startImportAsync_nonExistentFile_setsErrorStatus() throws InterruptedException {
        service.startImportAsync("/this/path/does/not/exist/Export.xml");
        // Give the daemon thread a moment to fail
        Thread.sleep(500);
        assertThat(service.getStatus()).isEqualTo("error");
        assertThat(service.getImportError()).contains("File not found");
    }

    // ── Concurrent import guard ───────────────────────────────────────────────

    @Test
    void startImportAsync_whileRunning_throwsIllegalStateException() throws Exception {
        // Force the status to "running" via reflection to bypass the timing race
        // that would occur if we relied on a background thread still being alive.
        Field statusField = HealthImportService.class.getDeclaredField("importStatus");
        statusField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<String> status = (AtomicReference<String>) statusField.get(service);
        status.set("running");

        assertThatThrownBy(() -> service.startImportAsync("/any/file.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
    }
}
