package ru.hpclab.hl.module1.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.hpclab.hl.module1.Configuration.SchoolJournalClientProperties;
import ru.hpclab.hl.module1.DTO.SubjectDTO;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    public SubjectCacheService(
            @Value("${subjectcache.printdelay}") int printDelay,
            RestTemplate restTemplate,
            SchoolJournalClientProperties clientProperties, ObservabilityService observabilityService) {
        this.printDelay = printDelay;
        this.restTemplate = restTemplate;
        this.clientProperties = clientProperties;
        this.observabilityService = observabilityService;
    }

    public Map<UUID, String> getSubjectCache() {
        if (subjectCache.isEmpty() || System.currentTimeMillis() - lastUpdateTime > TimeUnit.HOURS.toMillis(1)) {
            refreshCache();
        }
        return subjectCache;
    }

    public void refreshCache() {
        String subjectsUrl = clientProperties.getBaseUrl() + clientProperties.getEndpoints().getSubjects();
        observabilityService.start("cache.subject.externalCall");
        try {
            ResponseEntity<SubjectDTO[]> response = restTemplate.getForEntity(subjectsUrl, SubjectDTO[].class);
            SubjectDTO[] allSubjects = response.getBody();

            if (allSubjects != null && allSubjects.length > 0) {
                subjectCache = Arrays.stream(allSubjects)
                        .collect(Collectors.toMap(
                                SubjectDTO::getSubjectId,
                                SubjectDTO::getClassName
                        ));
                lastUpdateTime = System.currentTimeMillis();
            }
        } finally {
            observabilityService.stop("cache.subject.externalCall");
        }
    }

//    @Async
//    @Scheduled(fixedRateString = "${subjectcache.statistics.rate}")
//    public void printCacheStatistics() throws InterruptedException {
//        System.out.println(
//                Thread.currentThread().getName() + " - Subject cache statistics - " +
//                        printDelay + "ms - " + cacheInfoString + " - " +
//                        "Cache size: " + subjectCache.size() +
//                        ", Last updated: " + (lastUpdateTime > 0 ?
//                        (System.currentTimeMillis() - lastUpdateTime) / 1000 + " seconds ago" : "never"));
//        Thread.sleep(printDelay);
//    }

}
