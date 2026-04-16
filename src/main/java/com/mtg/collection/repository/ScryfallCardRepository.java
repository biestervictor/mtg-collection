package com.mtg.collection.repository;

import com.mtg.collection.model.ScryfallCard;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ScryfallCardRepository extends MongoRepository<ScryfallCard, String> {
    
    List<ScryfallCard> findBySetCode(String setCode);

    /** Batch-fetch cards for multiple set codes in a single query. */
    List<ScryfallCard> findBySetCodeIn(Collection<String> setCodes);

    /** Batch-fetch all printings whose name is in the given list. */
    List<ScryfallCard> findByNameIn(List<String> names);

    void deleteBySetCode(String setCode);
}
