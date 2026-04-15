package com.mtg.collection.repository;

import com.mtg.collection.model.UserDeck;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserDeckRepository extends MongoRepository<UserDeck, String> {

    List<UserDeck> findByUserOrderByCommanderDescNameAsc(String user);

    void deleteByUser(String user);
}
