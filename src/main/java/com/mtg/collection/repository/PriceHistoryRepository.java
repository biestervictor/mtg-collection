package com.mtg.collection.repository;

import com.mtg.collection.model.PriceHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends MongoRepository<PriceHistory, String> {

    List<PriceHistory> findByDate(LocalDate date);

    boolean existsByDate(LocalDate date);

    List<PriceHistory> findBySetCodeAndCollectorNumberOrderByDateAsc(
            String setCode, String collectorNumber);

    void deleteByDateBefore(LocalDate cutoff);
}
