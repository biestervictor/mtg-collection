package com.mtg.collection.repository;

import com.mtg.collection.model.ScryfallSet;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScryfallSetRepository extends MongoRepository<ScryfallSet, String> {
}
