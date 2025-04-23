package ru.hpclab.hl.module1.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassAverageDTO {
    private String className;
    private double averageGrade;
}