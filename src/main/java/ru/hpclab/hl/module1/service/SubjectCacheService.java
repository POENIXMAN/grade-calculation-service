package ru.hpclab.hl.module1.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.hpclab.hl.module1.Configuration.SchoolJournalClientProperties;
import ru.hpclab.hl.module1.DTO.SubjectDTO;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SubjectCacheService {

    @Value("${subjectcache.cachestatstring}")
    private String cacheInfoString;

    private final int printDelay;
    private final RestTemplate restTemplate;
    private final SchoolJournalClientProperties clientProperties;

    private Map<UUID, String> subjectCache = new HashMap<>();
    private long lastUpdateTime = 0;

    private final ObservabilityService observabilityService;

    private static final String CACHE_KEY = "subjectCache";
    private static final long CACHE_TTL_HOURS = 1;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    public SubjectCacheService(
            @Value("${subjectcache.printdelay}") int printDelay,
            RestTemplate restTemplate,
            SchoolJournalClientProperties clientProperties, ObservabilityService observabilityService, RedisTemplate<String, Object> redisTemplate, ValueOperations<String, Object> valueOps) {
        this.printDelay = printDelay;
        this.restTemplate = restTemplate;
        this.clientProperties = clientProperties;
        this.observabilityService = observabilityService;
        this.redisTemplate = redisTemplate;
        this.valueOps = valueOps;
    }

    @SuppressWarnings("unchecked")
    public Map<UUID, String> getSubjectCache() {
        Map<UUID, String> cache = (Map<UUID, String>) valueOps.get(CACHE_KEY);
        if (cache == null || cache.isEmpty()) {
            return refreshCache();
        }
        return cache;
    }

//    public Map<UUID, String> getSubjectCache() {
//        if (subjectCache.isEmpty() || System.currentTimeMillis() - lastUpdateTime > TimeUnit.HOURS.toMillis(1)) {
//            refreshCache();
//        }
//        return subjectCache;
//    }

    public Map<UUID, String> refreshCache() {
        String subjectsUrl = clientProperties.getBaseUrl() + clientProperties.getEndpoints().getSubjects();
        observabilityService.start("cache.subject.externalCall");
        try {
            ResponseEntity<SubjectDTO[]> response = restTemplate.getForEntity(subjectsUrl, SubjectDTO[].class);
            SubjectDTO[] allSubjects = response.getBody();

            if (allSubjects != null && allSubjects.length > 0) {
                Map<UUID, String> newCache = Arrays.stream(allSubjects)
                        .collect(Collectors.toMap(
                                SubjectDTO::getSubjectId,
                                SubjectDTO::getClassName
                        ));

                valueOps.set(CACHE_KEY, newCache, CACHE_TTL_HOURS, TimeUnit.HOURS);
                return newCache;
            }
        } finally {
            observabilityService.stop("cache.subject.externalCall");
        }
        return Collections.emptyMap();
    }

//    public void refreshCache() {
//        String subjectsUrl = clientProperties.getBaseUrl() + clientProperties.getEndpoints().getSubjects();
//        observabilityService.start("cache.subject.externalCall");
//        try {
//            ResponseEntity<SubjectDTO[]> response = restTemplate.getForEntity(subjectsUrl, SubjectDTO[].class);
//            SubjectDTO[] allSubjects = response.getBody();
//
//            if (allSubjects != null && allSubjects.length > 0) {
//                subjectCache = Arrays.stream(allSubjects)
//                        .collect(Collectors.toMap(
//                                SubjectDTO::getSubjectId,
//                                SubjectDTO::getClassName
//                        ));
//                lastUpdateTime = System.currentTimeMillis();
//            }
//        } finally {
//            observabilityService.stop("cache.subject.externalCall");
//        }
//    }

    @Async
    @Scheduled(fixedRateString = "${subjectcache.statistics.rate}")
    public void printCacheStatistics() throws InterruptedException {
        System.out.println(
                Thread.currentThread().getName() + " - Subject cache statistics - " +
                        printDelay + "ms - " + cacheInfoString + " - " +
                        "Cache size: " + subjectCache.size() +
                        ", Last updated: " + (lastUpdateTime > 0 ?
                        (System.currentTimeMillis() - lastUpdateTime) / 1000 + " seconds ago" : "never"));
        Thread.sleep(printDelay);
    }

}
