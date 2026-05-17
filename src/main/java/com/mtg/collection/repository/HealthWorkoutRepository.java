package com.mtg.collection.repository;

import com.mtg.collection.model.health.HealthWorkout;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface HealthWorkoutRepository extends MongoRepository<HealthWorkout, String> {

    List<HealthWorkout> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    List<HealthWorkout> findByActivityTypeAndDateBetweenOrderByDateAsc(String activityType, LocalDate from, LocalDate to);

    List<HealthWorkout> findAllByOrderByDateDesc();

    List<HealthWorkout> findTop10ByOrderByDateDesc();

    long countByActivityType(String activityType);

    long countByDateBetween(LocalDate from, LocalDate to);
}
