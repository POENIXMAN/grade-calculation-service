package ru.hpclab.hl.module1.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class ObservabilityService {
    // Configuration for time windows
    private static final int SHORT_WINDOW_SEC = 10;
    private static final int MEDIUM_WINDOW_SEC = 30;
    private static final int LONG_WINDOW_SEC = 60;

    // Storage for timing data (last minute)
    private final Deque<TimingRecord> timingRecords = new ConcurrentLinkedDeque<>();
    private final Map<String, AtomicLong> callCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounters = new ConcurrentHashMap<>();

    public enum OperationType {
        CONTROLLER_METHOD,
        EXTERNAL_SERVICE_CALL,
        DB_ACCESS,
        STATISTICS_CALCULATION
    }

    private static class TimingRecord {
        final OperationType type;
        final String methodName;
        final long durationMs;
        final long timestamp;
        final boolean success;

        TimingRecord(OperationType type, String methodName, long durationMs, boolean success) {
            this.type = type;
            this.methodName = methodName;
            this.durationMs = durationMs;
            this.timestamp = System.currentTimeMillis();
            this.success = success;
        }
    }

    public void recordTiming(OperationType type, String methodName, long durationMs, boolean success) {
        long now = System.currentTimeMillis();
        timingRecords.add(new TimingRecord(type, methodName, durationMs, success));

        // Clean up old records (older than 1 minute)
        while (!timingRecords.isEmpty() &&
                now - timingRecords.peekFirst().timestamp > TimeUnit.MINUTES.toMillis(1)) {
            timingRecords.removeFirst();
        }

        // Update counters
        String counterKey = type + ":" + methodName;
        callCounters.computeIfAbsent(counterKey, k -> new AtomicLong()).incrementAndGet();
        if (!success) {
            errorCounters.computeIfAbsent(counterKey, k -> new AtomicLong()).incrementAndGet();
        }
    }

    @Scheduled(fixedRate = 30000)
    public void calculateAndLogStatistics() {
        long now = System.currentTimeMillis();

        Map<String, Object> shortWindowStats = calculateStatsForWindow(now, SHORT_WINDOW_SEC);
        Map<String, Object> mediumWindowStats = calculateStatsForWindow(now, MEDIUM_WINDOW_SEC);
        Map<String, Object> longWindowStats = calculateStatsForWindow(now, LONG_WINDOW_SEC);

        // Format and print the statistics
        System.out.println("\n" + getCurrentDateTime() + " - Service Statistics");
        System.out.println("==========================================");

        printStatsForWindow("Last 10 seconds", shortWindowStats);
        printStatsForWindow("Last 30 seconds", mediumWindowStats);
        printStatsForWindow("Last 60 seconds", longWindowStats);

        System.out.println("==========================================");
    }

    private void printStatsForWindow(String windowTitle, Map<String, Object> stats) {
        System.out.println("\n" + windowTitle + ":");
        System.out.println("------------------------------------------");

        if (stats.isEmpty()) {
            System.out.println("  No activity in this time window");
            return;
        }

        for (Map.Entry<String, Object> typeEntry : stats.entrySet()) {
            String type = typeEntry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> methods = (Map<String, Object>) typeEntry.getValue();

            System.out.println("  " + type + ":");

            for (Map.Entry<String, Object> methodEntry : methods.entrySet()) {
                String method = methodEntry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = (Map<String, Object>) methodEntry.getValue();

                System.out.println("    " + method + ":");
                System.out.printf("      Count: %d\n", metrics.get("count"));
                System.out.printf("      Avg Time: %.2f ms\n", metrics.get("avg_ms"));
                System.out.printf("      Min Time: %d ms\n", metrics.get("min_ms"));
                System.out.printf("      Max Time: %d ms\n", metrics.get("max_ms"));
                System.out.printf("      Error Rate: %.2f%%\n", (double) metrics.get("error_rate") * 100);
                System.out.println("      --------------------");
            }
        }
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private Map<String, Object> calculateStatsForWindow(long currentTime, int windowSeconds) {
        long windowStart = currentTime - TimeUnit.SECONDS.toMillis(windowSeconds);

        List<TimingRecord> windowRecords = timingRecords.stream()
                .filter(record -> record.timestamp >= windowStart)
                .toList();

        Map<String, Object> stats = new HashMap<>();

        // Group by operation type and method
        Map<OperationType, Map<String, List<TimingRecord>>> grouped = windowRecords.stream()
                .collect(Collectors.groupingBy(
                        record -> record.type,
                        Collectors.groupingBy(record -> record.methodName)
                ));

        for (Map.Entry<OperationType, Map<String, List<TimingRecord>>> typeEntry : grouped.entrySet()) {
            Map<String, Object> typeStats = new HashMap<>();

            for (Map.Entry<String, List<TimingRecord>> methodEntry : typeEntry.getValue().entrySet()) {
                List<TimingRecord> records = methodEntry.getValue();
                List<Long> durations = records.stream().map(r -> r.durationMs).toList();

                Map<String, Object> methodStats = new HashMap<>();
                methodStats.put("count", durations.size());
                methodStats.put("avg_ms", durations.stream().mapToLong(l -> l).average().orElse(0));
                methodStats.put("max_ms", durations.stream().mapToLong(l -> l).max().orElse(0));
                methodStats.put("min_ms", durations.stream().mapToLong(l -> l).min().orElse(0));
                methodStats.put("error_rate", records.stream().filter(r -> !r.success).count() / (double) durations.size());

                typeStats.put(methodEntry.getKey(), methodStats);
            }

            stats.put(typeEntry.getKey().name(), typeStats);
        }

        return stats;
    }

    // Helper method to measure execution time
    public <T> T measure(OperationType type, String methodName, Supplier<T> operation) {
        long start = System.currentTimeMillis();
        try {
            T result = operation.get();
            recordTiming(type, methodName, System.currentTimeMillis() - start, true);
            return result;
        } catch (Exception e) {
            recordTiming(type, methodName, System.currentTimeMillis() - start, false);
            throw e;
        }
    }

    // For void operations
    public void measure(OperationType type, String methodName, Runnable operation) {
        long start = System.currentTimeMillis();
        try {
            operation.run();
            recordTiming(type, methodName, System.currentTimeMillis() - start, true);
        } catch (Exception e) {
            recordTiming(type, methodName, System.currentTimeMillis() - start, false);
            throw e;
        }
    }

}
