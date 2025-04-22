package ru.hpclab.hl.module1.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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
    private final CalculationRepository calculationRepository;

    @Autowired
    public CalculationService(
            RestTemplate restTemplate,
            CalculationRepository calculationRepository
    ) {
        this.restTemplate = restTemplate;
        this.calculationRepository = calculationRepository;
    }

    public double calculateAverageGradeForClass(UUID subjectId, int year) {
        Date startDate = getStartOfYear(year);
        Date endDate = getEndOfYear(year);

        // Fetch all grades from the grades service
        ResponseEntity<GradeDTO[]> response = restTemplate.getForEntity(
                "http://school-journal-app:8080/grades",
                GradeDTO[].class
        );

        GradeDTO[] allGrades = response.getBody();
        if (allGrades == null || allGrades.length == 0) {
            return 0.0;
        }

        // Filter grades by subject and date range in memory
        List<GradeDTO> filteredGrades = Arrays.stream(allGrades)
                .filter(grade -> subjectId.equals(grade.getSubjectId()))
                .filter(grade -> isDateInRange(grade.getGradingDate(), startDate, endDate))
                .toList();

        if (filteredGrades.isEmpty()) {
            return 0.0;
        }

        double sum = filteredGrades.stream()
                .mapToInt(GradeDTO::getGradeValue)
                .sum();

        return sum / filteredGrades.size();
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