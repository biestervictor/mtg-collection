package com.mtg.collection.repository;

import com.mtg.collection.model.UserCard;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCardRepository extends MongoRepository<UserCard, String> {
    
    List<UserCard> findByUser(String user);
    
    List<UserCard> findByUserAndSetCode(String user, String setCode);

    /** Returns ALL users' cards for a given set code (used for cross-user tradable lookups). */
    List<UserCard> findBySetCode(String setCode);

    void deleteByUser(String user);
}
