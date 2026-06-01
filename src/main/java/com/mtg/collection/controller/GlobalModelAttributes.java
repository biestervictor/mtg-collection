package com.mtg.collection.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private static final String DEV_HOST = "mongodb-service.treasury.svc.cluster.local";

    @Value("${app.version:-}")
    private String appVersion;

    @Value("${app.build.timestamp:-}")
    private String buildTimestamp;

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    /** Email address bound to the app user "Victor" — set via APP_USER_EMAIL_VICTOR env var. */
    @Value("${app.user-email.Victor:}")
    private String victorEmail;

    /** Email address bound to the app user "Andre" — set via APP_USER_EMAIL_ANDRE env var. */
    @Value("${app.user-email.Andre:}")
    private String andreEmail;

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

    /**
     * Resolves the logged-in Azure AD user to an app-level username ("Victor" / "Andre").
     * Matching uses the {@code email} OIDC claim (case-insensitive).
     * Returns {@code null} when no email mapping is configured or the user is not authenticated.
     */
    @ModelAttribute("currentAppUser")
    public String currentAppUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        if (!(auth.getPrincipal() instanceof OidcUser oidcUser)) return null;

        String email = oidcUser.getEmail();
        if (email == null) email = oidcUser.<String>getAttribute("preferred_username");
        if (email == null) return null;

        String emailLower = email.toLowerCase();
        if (!victorEmail.isEmpty() && victorEmail.toLowerCase().equals(emailLower)) return "Victor";
        if (!andreEmail.isEmpty()  && andreEmail.toLowerCase().equals(emailLower))  return "Andre";
        return null;
    }

    String extractHost(String uri) {
        if (uri == null || uri.isBlank()) return "";
        String work = uri;
        if (work.startsWith("mongodb://"))      work = work.substring("mongodb://".length());
        else if (work.startsWith("mongodb+srv://")) work = work.substring("mongodb+srv://".length());
        int atIdx = work.indexOf('@');
        if (atIdx >= 0) work = work.substring(atIdx + 1);
        int slashIdx = work.indexOf('/');
        if (slashIdx >= 0) work = work.substring(0, slashIdx);
        return work;
    }
}
