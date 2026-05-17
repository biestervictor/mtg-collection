package com.mtg.collection.repository;

import com.mtg.collection.model.health.HealthDailyMetric;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HealthDailyMetricRepository extends MongoRepository<HealthDailyMetric, String> {

    /** Returns all daily metrics with date >= from and date <= to (lexicographic ISO date compare). */
    List<HealthDailyMetric> findByDateBetweenOrderByDateAsc(String from, String to);

    List<HealthDailyMetric> findAllByOrderByDateDesc();

    List<HealthDailyMetric> findTop365ByOrderByDateDesc();
}
