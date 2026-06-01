package com.mtg.collection.controller;

import com.mtg.collection.model.UserEmailMapping;
import com.mtg.collection.repository.UserEmailMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserMappingControllerTest {

    @Mock
    private UserEmailMappingRepository mappingRepository;

    @InjectMocks
    private UserMappingController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers((viewName, locale) -> {
                    if (viewName.startsWith("redirect:")) {
                        return new RedirectView(viewName.substring("redirect:".length()), true);
                    }
                    return (model, request, response) -> response.setContentType("text/html");
                })
                .build();
        // Default: dev environment (not prod)
        ReflectionTestUtils.setField(controller, "mongoUri",
                "mongodb://mongodb-service.treasury.svc.cluster.local:27017/test");
    }

    // ── mapUser – happy path ──────────────────────────────────────────────────

    @Test
    void mapUser_savesVictorAndRedirects() throws Exception {
        mockMvc.perform(post("/api/user/map")
                        .param("appUser", "Victor")
                        .principal(oidcAuth("victor@example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        ArgumentCaptor<UserEmailMapping> cap = ArgumentCaptor.forClass(UserEmailMapping.class);
        verify(mappingRepository).save(cap.capture());
        assertEquals("victor@example.com", cap.getValue().getId());
        assertEquals("Victor", cap.getValue().getAppUser());
    }

    @Test
    void mapUser_savesAndreAndRedirects() throws Exception {
        mockMvc.perform(post("/api/user/map")
                        .param("appUser", "Andre")
                        .principal(oidcAuth("andre@example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        ArgumentCaptor<UserEmailMapping> cap = ArgumentCaptor.forClass(UserEmailMapping.class);
        verify(mappingRepository).save(cap.capture());
        assertEquals("Andre", cap.getValue().getAppUser());
    }

    @Test
    void mapUser_normalizesEmailToLowercase() throws Exception {
        mockMvc.perform(post("/api/user/map")
                        .param("appUser", "Victor")
                        .principal(oidcAuth("VICTOR@EXAMPLE.COM")))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<UserEmailMapping> cap = ArgumentCaptor.forClass(UserEmailMapping.class);
        verify(mappingRepository).save(cap.capture());
        assertEquals("victor@example.com", cap.getValue().getId());
    }

    // ── mapUser – validation ──────────────────────────────────────────────────

    @Test
    void mapUser_invalidAppUser_redirectsWithoutSaving() throws Exception {
        // No principal needed — controller rejects before reading auth
        mockMvc.perform(post("/api/user/map")
                        .param("appUser", "Hacker"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(mappingRepository, never()).save(any());
    }

    // ── mapUser – prod lock ───────────────────────────────────────────────────

    @Test
    void mapUser_prodLock_existingMappingIsRejected() throws Exception {
        // Configure prod environment
        ReflectionTestUtils.setField(controller, "mongoUri",
                "mongodb://192.168.178.141:27017/mtg_collection");
        when(mappingRepository.findById("victor@example.com"))
                .thenReturn(Optional.of(new UserEmailMapping("victor@example.com", "Victor")));

        mockMvc.perform(post("/api/user/map")
                        .param("appUser", "Andre")
                        .principal(oidcAuth("victor@example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(mappingRepository, never()).save(any());
    }

    @Test
    void mapUser_prodLock_newMappingIsAllowed() throws Exception {
        // Prod environment, but no existing mapping
        ReflectionTestUtils.setField(controller, "mongoUri",
                "mongodb://192.168.178.141:27017/mtg_collection");
        when(mappingRepository.findById("new@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/user/map")
                        .param("appUser", "Victor")
                        .principal(oidcAuth("new@example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(mappingRepository).save(any());
    }

    @Test
    void mapUser_devAllowsOverwrite_existingMapping() throws Exception {
        // Dev env — findById is never checked for prod-lock; re-mapping is allowed
        mockMvc.perform(post("/api/user/map")
                        .param("appUser", "Andre")
                        .principal(oidcAuth("victor@example.com")))
                .andExpect(status().is3xxRedirection());

        verify(mappingRepository).save(any());
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Authentication oidcAuth(String email) {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(email);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(oidcUser);
        return auth;
    }
}
