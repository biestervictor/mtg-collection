package com.mtg.collection.controller;

import com.mtg.collection.model.UserEmailMapping;
import com.mtg.collection.repository.UserEmailMappingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

/**
 * Handles the initial email → app-user mapping for newly authenticated users.
 *
 * <p>Rules:
 * <ul>
 *   <li>Any authenticated user without an existing mapping can call this once.</li>
 *   <li>In production the mapping is immutable once set (prod-lock).</li>
 *   <li>In dev/test a user may re-map their email (useful for testing).</li>
 * </ul>
 */
@Controller
public class UserMappingController {

    private static final Set<String> VALID_APP_USERS = Set.of("Victor", "Andre");
    private static final String DEV_HOST = "mongodb-service.treasury.svc.cluster.local";

    private final UserEmailMappingRepository mappingRepository;

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    public UserMappingController(UserEmailMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @PostMapping("/api/user/map")
    public String mapUser(@RequestParam String appUser,
                          Authentication auth,
                          RedirectAttributes redirectAttributes) {

        // 1. Validate chosen username
        if (!VALID_APP_USERS.contains(appUser)) {
            redirectAttributes.addFlashAttribute("mappingError", "Ungültiger Benutzername.");
            return "redirect:/";
        }

        // 2. Extract OIDC email
        String email = extractOidcEmail(auth);
        if (email == null) {
            redirectAttributes.addFlashAttribute("mappingError", "Kein gültiger OIDC-Login erkannt.");
            return "redirect:/";
        }

        // 3. Prod-lock: existing mapping cannot be changed in production
        if (isProd() && mappingRepository.findById(email).isPresent()) {
            redirectAttributes.addFlashAttribute("mappingError",
                    "In Produktion kann die Benutzerzuordnung nicht mehr geändert werden.");
            return "redirect:/";
        }

        // 4. Persist
        mappingRepository.save(new UserEmailMapping(email, appUser));
        return "redirect:/";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractOidcEmail(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        if (!(auth.getPrincipal() instanceof OidcUser oidcUser)) return null;
        String email = oidcUser.getEmail();
        if (email == null) email = oidcUser.<String>getAttribute("preferred_username");
        if (email == null) return null;
        return email.toLowerCase();
    }

    private boolean isProd() {
        String host = extractHost(mongoUri);
        return !host.isEmpty() && !host.contains(DEV_HOST);
    }

    private String extractHost(String uri) {
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
