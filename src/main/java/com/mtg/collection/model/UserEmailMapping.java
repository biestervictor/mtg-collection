package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persistent mapping from an OIDC email address to an app-level username ("Victor" / "Andre").
 * The email (lowercased) is used as the document ID to guarantee uniqueness.
 * Once persisted in production this mapping is read-only.
 */
@Document("user_email_mappings")
public class UserEmailMapping {

    /** Lowercase email address — acts as the unique document ID. */
    @Id
    private String id;

    /** App-level username, one of: "Victor", "Andre". */
    private String appUser;

    private Instant createdAt;

    public UserEmailMapping() {}

    public UserEmailMapping(String email, String appUser) {
        this.id        = email.toLowerCase();
        this.appUser   = appUser;
        this.createdAt = Instant.now();
    }

    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getAppUser()              { return appUser; }
    public void   setAppUser(String u)      { this.appUser = u; }

    public Instant getCreatedAt()           { return createdAt; }
    public void    setCreatedAt(Instant t)  { this.createdAt = t; }
}
