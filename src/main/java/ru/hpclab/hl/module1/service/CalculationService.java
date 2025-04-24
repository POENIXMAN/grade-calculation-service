package ru.hpclab.hl.module1.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.hpclab.hl.module1.Configuration.SchoolJournalClientProperties;
import ru.hpclab.hl.module1.DTO.ClassAverageDTO;
import ru.hpclab.hl.module1.DTO.GradeDTO;
import ru.hpclab.hl.module1.DTO.SubjectDTO;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class CalculationService {
    private final RestTemplate restTemplate;
    private final SchoolJournalClientProperties clientProperties;
    private final ObservabilityService observabilityService;

    @Autowired
    public CalculationService(
            RestTemplate restTemplate, SchoolJournalClientProperties clientProperties, ObservabilityService observabilityService
    ) {
        this.restTemplate = restTemplate;
        this.clientProperties = clientProperties;
        this.observabilityService = observabilityService;
    }

    public List<ClassAverageDTO> calculateAverageGradesForAllClasses(int year) {
        observabilityService.start("service.calculateAverageGradesForAllClasses.total");
        try {
            Date startDate = getStartOfYear(year);
            Date endDate = getEndOfYear(year);

            String gradesUrl = clientProperties.getBaseUrl() + clientProperties.getEndpoints().getGrades();
            String subjectsUrl = clientProperties.getBaseUrl() + clientProperties.getEndpoints().getSubjects();

            observabilityService.start("service.calculateAverageGradesForAllClasses.api_call_grades");

            ResponseEntity<GradeDTO[]> gradesResponse = restTemplate.getForEntity(
                    gradesUrl,
                    GradeDTO[].class
            );

            GradeDTO[] allGrades = gradesResponse.getBody();
            observabilityService.stop("service.calculateAverageGradesForAllClasses.api_call_grades");

            observabilityService.start("service.calculateAverageGradesForAllClasses.api_call_students");
            ResponseEntity<SubjectDTO[]> subjectsResponse = restTemplate.getForEntity(
                    subjectsUrl,
                    SubjectDTO[].class
            );

            SubjectDTO[] allSubjects = subjectsResponse.getBody();
            observabilityService.stop("service.calculateAverageGradesForAllClasses.api_call_students");

            if (allGrades == null || allGrades.length == 0 || allSubjects == null || allSubjects.length == 0) {
                return Collections.emptyList();
            }

            Map<UUID, String> subjectIdToClassName = Arrays.stream(allSubjects)
                    .collect(Collectors.toMap(
                            SubjectDTO::getSubjectId,
                            SubjectDTO::getClassName
                    ));

            List<GradeDTO> gradesInYear = Arrays.stream(allGrades)
                    .filter(grade -> isDateInRange(grade.getGradingDate(), startDate, endDate))
                    .toList();

            Map<UUID, Double> averages = new HashMap<>();
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
        } finally {
            observabilityService.stop("service.calculateAverageGradesForAllClasses.total");
        }

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