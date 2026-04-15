package com.mtg.collection.repository;

import com.mtg.collection.model.MetaDeck;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetaDeckRepository extends MongoRepository<MetaDeck, String> {

    List<MetaDeck> findByFormat(String format);

    void deleteByFormat(String format);
}
