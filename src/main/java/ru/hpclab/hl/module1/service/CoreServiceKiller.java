package ru.hpclab.hl.module1.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.*;


@Service
public class CoreServiceKiller {
    private final RestTemplate restTemplate;
    private final String crashUrl;
    private static final Logger log = LoggerFactory.getLogger(CoreServiceKiller.class);

    public CoreServiceKiller(
            RestTemplate restTemplate,
            @Value("${school-journal.base-url}") String baseUrl,
            @Value("${school-journal.endpoints.coreCrash}") String crashEndpoint
    ) {
        this.restTemplate = restTemplate;
        this.crashUrl = baseUrl + crashEndpoint;
        log.info("Configured crash endpoint: {}", crashUrl);
    }

    @Scheduled(fixedRate = 10000)
    public void triggerCrash() {
        try {
            log.info("Attempting to crash core service...");
            restTemplate.postForEntity(crashUrl, null, String.class);
            log.error("Core service DID NOT crash as expected!");
        }
        catch (ResourceAccessException e) {
            log.warn("Expected crash behavior: {}", e.getMessage());
        }
    }
}