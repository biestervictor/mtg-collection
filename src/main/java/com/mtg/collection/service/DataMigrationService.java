package com.mtg.collection.service;

import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

    private static final Map<String, String> USER_MIGRATIONS = Map.of(
            "user1", "Andre",
            "user2", "Victor",
            "user3", "Andre",
            "user4", "Marcel"
    );

    private final UserCardRepository userCardRepository;
    private final ImportHistoryRepository importHistoryRepository;

    public DataMigrationService(UserCardRepository userCardRepository,
                                ImportHistoryRepository importHistoryRepository) {
        this.userCardRepository = userCardRepository;
        this.importHistoryRepository = importHistoryRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrations() {
        USER_MIGRATIONS.forEach(this::migrateUser);
    }

    private void migrateUser(String oldName, String newName) {
        List<UserCard> cards = userCardRepository.findByUser(oldName);
        if (!cards.isEmpty()) {
            log.info("Migrating {} UserCards from '{}' to '{}'", cards.size(), oldName, newName);
            cards.forEach(c -> c.setUser(newName));
            userCardRepository.saveAll(cards);
        }

        List<ImportHistory> histories = importHistoryRepository.findByUserOrderByImportedAtDesc(oldName);
        if (!histories.isEmpty()) {
            log.info("Migrating {} ImportHistory entries from '{}' to '{}'", histories.size(), oldName, newName);
            histories.forEach(h -> h.setUser(newName));
            importHistoryRepository.saveAll(histories);
        }
    }
}
