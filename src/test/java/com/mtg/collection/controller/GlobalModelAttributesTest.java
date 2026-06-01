package com.mtg.collection.controller;

import com.mtg.collection.model.UserEmailMapping;
import com.mtg.collection.repository.UserEmailMappingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalModelAttributesTest {

    private UserEmailMappingRepository mockRepo;
    private GlobalModelAttributes advice;

    @BeforeEach
    void setUp() {
        mockRepo = mock(UserEmailMappingRepository.class);
        advice   = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mappingRepository", mockRepo);
    }

    // ── appVersion ────────────────────────────────────────────────────────────

    @Test
    void appVersion_returnsInjectedValue() {
        ReflectionTestUtils.setField(advice, "appVersion", "1.2.3");
        assertEquals("1.2.3", advice.appVersion());
    }

    @Test
    void appVersion_fallsBackToDevWhenEmpty() {
        ReflectionTestUtils.setField(advice, "appVersion", "");
        assertEquals("dev", advice.appVersion());
    }

    // ── buildTimestamp ────────────────────────────────────────────────────────

    @Test
    void buildTimestamp_returnsInjectedValue() {
        ReflectionTestUtils.setField(advice, "buildTimestamp", "2026-04-07T13:00:00Z");
        assertEquals("2026-04-07T13:00:00Z", advice.buildTimestamp());
    }

    @Test
    void buildTimestamp_returnsEmptyWhenBlank() {
        ReflectionTestUtils.setField(advice, "buildTimestamp", "");
        assertEquals("", advice.buildTimestamp());
    }

    // ── isProd ────────────────────────────────────────────────────────────────

    @Test
    void isProd_falseForDevHost() {
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://mongodb-service.treasury.svc.cluster.local:27017/mtg_collection");
        assertFalse(advice.isProd());
    }

    @Test
    void isProd_trueForProdHost() {
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://192.168.178.141:27017/mtg_collection");
        assertTrue(advice.isProd());
    }

    @Test
    void isProd_trueForProdHostWithCredentials() {
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://user:pass@192.168.178.141:27017/mtg_collection?authSource=admin");
        assertTrue(advice.isProd());
    }

    @Test
    void isProd_falseForEmptyUri() {
        ReflectionTestUtils.setField(advice, "mongoUri", "");
        assertFalse(advice.isProd());
    }

    // ── mongoHost ─────────────────────────────────────────────────────────────

    @Test
    void mongoHost_extractsHostFromSimpleUri() {
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://192.168.178.141:27017/mtg_collection");
        assertEquals("192.168.178.141:27017", advice.mongoHost());
    }

    @Test
    void mongoHost_extractsHostFromUriWithCredentials() {
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://user:pass@myhost.example.com:27017/db?authSource=admin");
        assertEquals("myhost.example.com:27017", advice.mongoHost());
    }

    @Test
    void mongoHost_returnsEmptyForBlankUri() {
        ReflectionTestUtils.setField(advice, "mongoUri", "");
        assertEquals("", advice.mongoHost());
    }

    @Test
    void extractHost_handlesSrvUri() {
        assertEquals("cluster0.mongodb.net",
                advice.extractHost("mongodb+srv://user:pass@cluster0.mongodb.net/db"));
    }

    // ── currentUri ────────────────────────────────────────────────────────────

    @Test
    void currentUri_returnsRequestUri() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/");
        assertEquals("/", advice.currentUri(req));
    }

    @Test
    void currentUri_returnsSubpathUri() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/import");
        assertEquals("/import", advice.currentUri(req));
    }

    @Test
    void currentUri_returnsEmptyStringWhenRequestIsNull() {
        assertEquals("", advice.currentUri(null));
    }

    // ── currentAppUser ────────────────────────────────────────────────────────

    @Test
    void currentAppUser_returnsVictorWhenMappingExists() {
        when(mockRepo.findById("victor@example.com"))
                .thenReturn(Optional.of(new UserEmailMapping("victor@example.com", "Victor")));

        assertEquals("Victor", advice.currentAppUser(authenticatedAs("victor@example.com")));
    }

    @Test
    void currentAppUser_returnsAndreWhenMappingExists() {
        when(mockRepo.findById("andre@example.com"))
                .thenReturn(Optional.of(new UserEmailMapping("andre@example.com", "Andre")));

        assertEquals("Andre", advice.currentAppUser(authenticatedAs("andre@example.com")));
    }

    @Test
    void currentAppUser_returnsNullWhenNoMapping() {
        when(mockRepo.findById("stranger@example.com")).thenReturn(Optional.empty());
        assertNull(advice.currentAppUser(authenticatedAs("stranger@example.com")));
    }

    @Test
    void currentAppUser_returnsNullWhenUnauthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        assertNull(advice.currentAppUser(auth));
    }

    @Test
    void currentAppUser_returnsNullWhenAuthIsNull() {
        assertNull(advice.currentAppUser(null));
    }

    @Test
    void currentAppUser_returnsNullForNonOidcPrincipal() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("not-an-oidc-user");
        assertNull(advice.currentAppUser(auth));
    }

    // ── userMappingRequired ───────────────────────────────────────────────────

    @Test
    void userMappingRequired_trueWhenNoMappingExists() {
        when(mockRepo.findById("new@example.com")).thenReturn(Optional.empty());
        assertTrue(advice.userMappingRequired(authenticatedAs("new@example.com")));
    }

    @Test
    void userMappingRequired_falseWhenMappingExists() {
        when(mockRepo.findById("victor@example.com"))
                .thenReturn(Optional.of(new UserEmailMapping("victor@example.com", "Victor")));
        assertFalse(advice.userMappingRequired(authenticatedAs("victor@example.com")));
    }

    @Test
    void userMappingRequired_falseWhenUnauthenticated() {
        assertFalse(advice.userMappingRequired(null));
    }

    // ── extractOidcEmail ──────────────────────────────────────────────────────

    @Test
    void extractOidcEmail_normalizesToLowercase() {
        assertEquals("victor@example.com", advice.extractOidcEmail(authenticatedAs("VICTOR@EXAMPLE.COM")));
    }

    @Test
    void extractOidcEmail_fallsBackToPreferredUsernameWhenEmailNull() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(null);
        when(oidcUser.<String>getAttribute("preferred_username")).thenReturn("andre@example.com");
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(oidcUser);

        assertEquals("andre@example.com", advice.extractOidcEmail(auth));
    }

    @Test
    void extractOidcEmail_returnsNullWhenBothClaimsAbsent() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(null);
        when(oidcUser.<String>getAttribute("preferred_username")).thenReturn(null);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(oidcUser);

        assertNull(advice.extractOidcEmail(auth));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Authentication authenticatedAs(String email) {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(email);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(oidcUser);
        return auth;
    }
}
