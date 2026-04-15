package com.mtg.collection.repository;

import com.mtg.collection.model.ImportHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ImportHistoryRepository extends MongoRepository<ImportHistory, String> {
    
    List<ImportHistory> findByUserOrderByImportedAtDesc(String user);

    void deleteByUser(String user);
}
