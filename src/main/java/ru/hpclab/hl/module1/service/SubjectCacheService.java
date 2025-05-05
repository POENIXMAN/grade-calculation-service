package ru.hpclab.hl.module1.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.hpclab.hl.module1.Configuration.SchoolJournalClientProperties;
import ru.hpclab.hl.module1.DTO.SubjectDTO;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SubjectCacheService {
        private static final String CACHE_KEY = "subjectCache";
        private static final long CACHE_TTL_HOURS = 1;

        private final RedisTemplate<String, Object> redisTemplate;
        private final RestTemplate restTemplate;
        private final SchoolJournalClientProperties clientProperties;
        private final ObservabilityService observabilityService;

        public SubjectCacheService(
                RedisTemplate<String, Object> redisTemplate,
                RestTemplate restTemplate,
                SchoolJournalClientProperties clientProperties,
                ObservabilityService observabilityService) {
            this.redisTemplate = redisTemplate;
            this.restTemplate = restTemplate;
            this.clientProperties = clientProperties;
            this.observabilityService = observabilityService;
        }

    @SuppressWarnings("unchecked")
    public Map<UUID, String> getSubjectCache() {
        observabilityService.start("cache.subject.access.total");
        try {
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

            observabilityService.start("cache.subject.redis.get");
            Map<UUID, String> cache = (Map<UUID, String>) valueOps.get(CACHE_KEY);
            observabilityService.stop("cache.subject.redis.get");

            if (cache == null || cache.isEmpty()) {
                observabilityService.start("cache.subject.access.miss");
                try {
                    return refreshCache();
                } finally {
                    observabilityService.stop("cache.subject.access.miss");
                }
            } else {
                // Cache hit (return cached data)
                observabilityService.start("cache.subject.access.hit");
                observabilityService.stop("cache.subject.access.hit");
                return cache;
            }
        } finally {
            observabilityService.stop("cache.subject.access.total");
        }
    }

        @SuppressWarnings("unchecked")
//        public Map<UUID, String> getSubjectCache() {
//            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
//            Map<UUID, String> cache = (Map<UUID, String>) valueOps.get(CACHE_KEY);
//            if (cache == null || cache.isEmpty()) {
//                return refreshCache();
//            }
//            return cache;
//        }

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

                    redisTemplate.opsForValue().set(CACHE_KEY, newCache, CACHE_TTL_HOURS, TimeUnit.HOURS);
                    return newCache;
                }
            } finally {
                observabilityService.stop("cache.subject.externalCall");
            }
            return Collections.emptyMap();
        }


    @Async
    @Scheduled(fixedRateString = "${subjectcache.statistics.rate}")
    public void printCacheStatistics() throws InterruptedException {
        System.out.println(
                Thread.currentThread().getName() + " - Subject cache statistics - " +
                        10000 + "ms - " + "Cache size: " + getSubjectCache().size());
        Thread.sleep(10000);
    }

}
