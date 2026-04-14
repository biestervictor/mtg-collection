package com.mtg.collection.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalModelAttributesTest {

    @Test
    void appVersion_returnsInjectedValue() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "appVersion", "1.2.3");

        assertEquals("1.2.3", advice.appVersion());
    }

    @Test
    void appVersion_fallsBackToDevWhenEmpty() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "appVersion", "");

        assertEquals("dev", advice.appVersion());
    }

    @Test
    void buildTimestamp_returnsInjectedValue() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "buildTimestamp", "2026-04-07T13:00:00Z");

        assertEquals("2026-04-07T13:00:00Z", advice.buildTimestamp());
    }

    @Test
    void buildTimestamp_returnsEmptyWhenBlank() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "buildTimestamp", "");

        assertEquals("", advice.buildTimestamp());
    }

    @Test
    void isProd_falseForDevHost() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://mongodb-service.treasury.svc.cluster.local:27017/mtg_collection");

        assertFalse(advice.isProd());
    }

    @Test
    void isProd_trueForProdHost() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://192.168.178.141:27017/mtg_collection");

        assertTrue(advice.isProd());
    }

    @Test
    void isProd_trueForProdHostWithCredentials() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://user:pass@192.168.178.141:27017/mtg_collection?authSource=admin");

        assertTrue(advice.isProd());
    }

    @Test
    void isProd_falseForEmptyUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri", "");

        assertFalse(advice.isProd());
    }

    @Test
    void mongoHost_extractsHostFromSimpleUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://192.168.178.141:27017/mtg_collection");

        assertEquals("192.168.178.141:27017", advice.mongoHost());
    }

    @Test
    void mongoHost_extractsHostFromUriWithCredentials() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri",
                "mongodb://user:pass@myhost.example.com:27017/db?authSource=admin");

        assertEquals("myhost.example.com:27017", advice.mongoHost());
    }

    @Test
    void mongoHost_returnsEmptyForBlankUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        ReflectionTestUtils.setField(advice, "mongoUri", "");

        assertEquals("", advice.mongoHost());
    }

    @Test
    void extractHost_handlesSrvUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        assertEquals("cluster0.mongodb.net", advice.extractHost("mongodb+srv://user:pass@cluster0.mongodb.net/db"));
    }

    @Test
    void currentUri_returnsRequestUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/");

        assertEquals("/", advice.currentUri(request));
    }

    @Test
    void currentUri_returnsSubpathUri() {
        GlobalModelAttributes advice = new GlobalModelAttributes();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/import");

        assertEquals("/import", advice.currentUri(request));
    }

    @Test
    void currentUri_returnsEmptyStringWhenRequestIsNull() {
        GlobalModelAttributes advice = new GlobalModelAttributes();

        assertEquals("", advice.currentUri(null));
    }
}
