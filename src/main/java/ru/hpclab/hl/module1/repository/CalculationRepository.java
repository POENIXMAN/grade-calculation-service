package ru.hpclab.hl.module1.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.hpclab.hl.module1.Entity.GradeEntity;

import java.util.Date;
import java.util.List;
import java.util.UUID;


@Repository
public interface CalculationRepository extends JpaRepository<GradeEntity, UUID> {


    @Query("SELECT g FROM GradeEntity g WHERE g.subjectEntity.identifier = :subjectId " +
            "AND g.gradingDate BETWEEN :startDate AND :endDate")
    List<GradeEntity> findBySubjectAndGradingDateBetween(
            @Param("subjectId") UUID subjectId,
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate);
}
