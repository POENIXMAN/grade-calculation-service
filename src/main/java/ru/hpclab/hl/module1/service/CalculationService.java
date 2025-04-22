package ru.hpclab.hl.module1.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.hpclab.hl.module1.Entity.GradeEntity;
import ru.hpclab.hl.module1.repository.CalculationRepository;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;


@Service
public class CalculationService {
    private final CalculationRepository calculationRepository;

    @Autowired
    public CalculationService(CalculationRepository gradeRepository) {
        this.calculationRepository = gradeRepository;
    }

    public double calculateAverageGradeForClass(UUID subjectId, int year) {
        Date startDate = getStartOfYear(year);
        Date endDate = getEndOfYear(year);

        List<GradeEntity> grades = calculationRepository.findBySubjectAndGradingDateBetween(subjectId, startDate, endDate);

        if (grades.isEmpty()) {
            return 0.0;
        }

        double sum = grades.stream().mapToInt(GradeEntity::getGradeValue).sum();
        return sum / grades.size();
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
