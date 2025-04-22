package ru.hpclab.hl.module1.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.hpclab.hl.module1.DTO.GradeDTO;
import ru.hpclab.hl.module1.Entity.GradeEntity;
import ru.hpclab.hl.module1.repository.CalculationRepository;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class CalculationService {
    private final RestTemplate restTemplate;
    private final String gradeServiceUrl;

    @Autowired
    public CalculationService(RestTemplate restTemplate,
                              @Value("${grade.service.url}") String gradeServiceUrl) {
        this.restTemplate = restTemplate;
        this.gradeServiceUrl = gradeServiceUrl;
    }

    public double calculateAverageGradeForClass(UUID subjectId, int year) {
        // 1. Get all grades from main service
        GradeDTO[] grades = restTemplate.getForObject(
                gradeServiceUrl + "/grades",
                GradeDTO[].class
        );

        if (grades == null || grades.length == 0) {
            return 0.0;
        }

        // 2. Filter and calculate in Java (not DB)
        Date startDate = getStartOfYear(year);
        Date endDate = getEndOfYear(year);

        List<GradeDTO> filteredGrades = Arrays.stream(grades)
                .filter(g -> g.getSubjectId().equals(subjectId))
                .filter(g -> !g.getGradingDate().before(startDate))
                .filter(g -> !g.getGradingDate().after(endDate))
                .toList();

        if (filteredGrades.isEmpty()) {
            return 0.0;
        }

        // 3. Calculate average
        return filteredGrades.stream()
                .mapToInt(GradeDTO::getGradeValue)
                .average()
                .orElse(0.0);
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
