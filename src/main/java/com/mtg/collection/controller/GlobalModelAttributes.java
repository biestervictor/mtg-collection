package com.mtg.collection.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private static final String DEV_HOST = "mongodb-service.dev.svc.cluster.local";

    @Value("${app.version:-}")
    private String appVersion;

    @Value("${app.build.timestamp:-}")
    private String buildTimestamp;

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

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

    String extractHost(String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        String work = uri;
        if (work.startsWith("mongodb://")) {
            work = work.substring("mongodb://".length());
        } else if (work.startsWith("mongodb+srv://")) {
            work = work.substring("mongodb+srv://".length());
        }
        int atIdx = work.indexOf('@');
        if (atIdx >= 0) {
            work = work.substring(atIdx + 1);
        }
        int slashIdx = work.indexOf('/');
        if (slashIdx >= 0) {
            work = work.substring(0, slashIdx);
        }
        return work;
    }
}