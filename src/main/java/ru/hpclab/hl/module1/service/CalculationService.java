package ru.hpclab.hl.module1.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.hpclab.hl.module1.Configuration.SchoolJournalClientProperties;
import ru.hpclab.hl.module1.DTO.ClassAverageDTO;
import ru.hpclab.hl.module1.DTO.GradeDTO;
import ru.hpclab.hl.module1.DTO.SubjectDTO;
import ru.hpclab.hl.module1.Entity.GradeEntity;
import ru.hpclab.hl.module1.repository.CalculationRepository;

import javax.security.auth.Subject;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class CalculationService {
    private final RestTemplate restTemplate;
    private final SchoolJournalClientProperties clientProperties;
    private final SubjectCacheService subjectCacheService;
    private final ObservabilityService observabilityService;

    @Autowired
    public CalculationService(
            RestTemplate restTemplate, SchoolJournalClientProperties clientProperties, SubjectCacheService subjectCacheService, ObservabilityService observabilityService
    ) {
        this.restTemplate = restTemplate;
        this.clientProperties = clientProperties;
        this.subjectCacheService = subjectCacheService;
        this.observabilityService = observabilityService;
    }

    public List<ClassAverageDTO> calculateAverageGradesForAllClasses(int year) {
        return observabilityService.measure(
                ObservabilityService.OperationType.STATISTICS_CALCULATION,
                "calculateAverageGradesForAllClasses",
                () -> {
                    Date startDate = getStartOfYear(year);
                    Date endDate = getEndOfYear(year);

                    GradeDTO[] allGrades = observabilityService.measure(
                            ObservabilityService.OperationType.EXTERNAL_SERVICE_CALL,
                            "getGradesFromExternalService",
                            () -> {
                                String gradesUrl = clientProperties.getBaseUrl() + clientProperties.getEndpoints().getGrades();
                                ResponseEntity<GradeDTO[]> gradesResponse = restTemplate.getForEntity(
                                        gradesUrl,
                                        GradeDTO[].class
                                );
                                return gradesResponse.getBody();
                            }
                    );

                    if (allGrades == null || allGrades.length == 0) {
                        return Collections.emptyList();
                    }

                    Map<UUID, String> subjectIdToClassName = subjectCacheService.getSubjectCache();
                    if (subjectIdToClassName.isEmpty()) {
                        return Collections.emptyList();
                    }


                    List<GradeDTO> gradesInYear = Arrays.stream(allGrades)
                            .filter(grade -> isDateInRange(grade.getGradingDate(), startDate, endDate))
                            .toList();

                    Map<String, List<Integer>> classNameToGrades = new HashMap<>();

                    for (GradeDTO grade : gradesInYear) {
                        String className = subjectIdToClassName.get(grade.getSubjectId());
                        if (className != null) {
                            classNameToGrades.computeIfAbsent(className, k -> new ArrayList<>())
                                    .add(grade.getGradeValue());
                        }
                    }

                    List<ClassAverageDTO> result = new ArrayList<>();
                    for (Map.Entry<String, List<Integer>> entry : classNameToGrades.entrySet()) {
                        double average = entry.getValue().stream()
                                .mapToInt(Integer::intValue)
                                .average()
                                .orElse(0.0);
                        result.add(new ClassAverageDTO(entry.getKey(), average));
                    }

                    return result;
                }
        );
    }

    private boolean isDateInRange(Date dateToCheck, Date startDate, Date endDate) {
        return !dateToCheck.before(startDate) && !dateToCheck.after(endDate);
    }

    private Date getStartOfYear(int year) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0);
        return calendar.getTime();
    }

    private Date getEndOfYear(int year) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, Calendar.DECEMBER, 31, 23, 59, 59);
        return calendar.getTime();
    }
}