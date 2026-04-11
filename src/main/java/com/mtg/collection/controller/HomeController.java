package com.mtg.collection.controller;

import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.service.ScryfallService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final ScryfallService scryfallService;

    public HomeController(ScryfallService scryfallService) {
        this.scryfallService = scryfallService;
    }

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }
}
