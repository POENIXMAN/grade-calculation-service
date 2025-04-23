package ru.hpclab.hl.module1.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.hpclab.hl.module1.DTO.ClassAverageDTO;
import ru.hpclab.hl.module1.service.CalculationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/calculations")
public class CalculationController {
    private final CalculationService calculationService;

    @Autowired
    public CalculationController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

//    @GetMapping("/average/class/{subjectId}/year/{year}")
//    public double calculateAverageGradeForClass(
//            @PathVariable UUID subjectId,
//            @PathVariable int year) {
//        return calculationService.calculateAverageGradeForClass(subjectId, year);
//    }

    @GetMapping("/average/year/{year}")
    public List<ClassAverageDTO> calculateAverageGradesForAllClasses(@PathVariable int year) {
        return calculationService.calculateAverageGradesForAllClasses(year);
    }
}


