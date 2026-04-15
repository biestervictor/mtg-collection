package com.mtg.collection.controller;

import com.mtg.collection.model.UserDeck;
import com.mtg.collection.service.UserDeckService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/my-decks")
public class MyDecksController {

    private final UserDeckService userDeckService;

    public MyDecksController(UserDeckService userDeckService) {
        this.userDeckService = userDeckService;
    }

    @GetMapping
    public String myDecks(@RequestParam(defaultValue = "Victor") String user, Model model) {
        List<UserDeck> decks = userDeckService.getDecksForUser(user);
        model.addAttribute("decks", decks);
        model.addAttribute("user", user);
        return "my-decks";
    }
}
