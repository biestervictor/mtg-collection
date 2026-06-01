package com.mtg.collection.controller;

import com.mtg.collection.repository.UserEmailMappingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private static final String DEV_HOST = "mongodb-service.treasury.svc.cluster.local";

    @Autowired
    private UserEmailMappingRepository mappingRepository;

    @Value("${app.version:-}")
    private String appVersion;

    @Value("${app.build.timestamp:-}")
    private String buildTimestamp;

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    // ── Standard model attributes ─────────────────────────────────────────────

    @ModelAttribute("appVersion")
    public String appVersion() {
        return appVersion != null && !appVersion.isEmpty() ? appVersion : "dev";
    }

    @ModelAttribute("buildTimestamp")
    public String buildTimestamp() {
        return buildTimestamp != null && !buildTimestamp.isEmpty() ? buildTimestamp : "";
    }

    @ModelAttribute("mongoHost")
    public String mongoHost() {
        return extractHost(mongoUri);
    }

    @ModelAttribute("isProd")
    public boolean isProd() {
        String host = extractHost(mongoUri);
        return !host.isEmpty() && !host.contains(DEV_HOST);
    }

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "";
    }

    // ── App-user resolution via DB ────────────────────────────────────────────

    /**
     * Resolves the logged-in OIDC user to an app-level username ("Victor" / "Andre")
     * by looking up the email in the {@code user_email_mappings} collection.
     * Returns {@code null} when no mapping exists yet.
     */
    @ModelAttribute("currentAppUser")
    public String currentAppUser(Authentication auth) {
        String email = extractOidcEmail(auth);
        if (email == null) return null;
        return mappingRepository.findById(email)
                .map(m -> m.getAppUser())
                .orElse(null);
    }

    /**
     * Returns {@code true} when the authenticated OIDC user has no email mapping yet
     * and needs to select their app username.
     */
    @ModelAttribute("userMappingRequired")
    public boolean userMappingRequired(Authentication auth) {
        String email = extractOidcEmail(auth);
        if (email == null) return false;
        return mappingRepository.findById(email).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the lowercased email from the authenticated OIDC principal.
     * Falls back to the {@code preferred_username} claim when the {@code email} claim is absent.
     * Returns {@code null} for unauthenticated or non-OIDC principals.
     */
    String extractOidcEmail(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        if (!(auth.getPrincipal() instanceof OidcUser oidcUser)) return null;
        String email = oidcUser.getEmail();
        if (email == null) email = oidcUser.<String>getAttribute("preferred_username");
        if (email == null) return null;
        return email.toLowerCase();
    }

    String extractHost(String uri) {
        if (uri == null || uri.isBlank()) return "";
        String work = uri;
        if (work.startsWith("mongodb://"))         work = work.substring("mongodb://".length());
        else if (work.startsWith("mongodb+srv://")) work = work.substring("mongodb+srv://".length());
        int atIdx = work.indexOf('@');
        if (atIdx >= 0) work = work.substring(atIdx + 1);
        int slashIdx = work.indexOf('/');
        if (slashIdx >= 0) work = work.substring(0, slashIdx);
        return work;
    }
}
