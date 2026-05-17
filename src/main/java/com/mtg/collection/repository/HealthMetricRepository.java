package com.mtg.collection.repository;

import com.mtg.collection.model.health.HealthMetric;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthMetricRepository extends MongoRepository<HealthMetric, String> {

    List<HealthMetric> findByTypeOrderByDateAsc(String type);

    List<HealthMetric> findByTypeAndDateBetweenOrderByDateAsc(String type, LocalDate from, LocalDate to);

    Optional<HealthMetric> findTopByTypeOrderByDateDesc(String type);

    @Query("{'type': ?0, 'date': {$gte: ?1, $lte: ?2}}")
    List<HealthMetric> findByTypeAndDateRange(String type, LocalDate from, LocalDate to);

    long countByType(String type);

    void deleteByType(String type);
}
