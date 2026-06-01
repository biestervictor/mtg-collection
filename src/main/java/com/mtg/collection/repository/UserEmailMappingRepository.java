package com.mtg.collection.repository;

import com.mtg.collection.model.UserEmailMapping;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for email → app-user mappings.
 * The document ID is the lowercase email address, so {@code findById(email)} is the primary lookup.
 */
public interface UserEmailMappingRepository extends MongoRepository<UserEmailMapping, String> {
}
