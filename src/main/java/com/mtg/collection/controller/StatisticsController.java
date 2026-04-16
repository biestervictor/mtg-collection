package com.mtg.collection.controller;

import com.mtg.collection.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public String statisticsPage(Model model, @RequestParam(required = false) String user) {

        if (user != null && !user.isEmpty()) {
            // User selected: compute only their stats; get the user list cheaply
            // for the dropdown (no full stats for other users needed here).
            List<String> users = statisticsService.getDistinctUsers();
            Map<String, com.mtg.collection.dto.UserStatistics> allStats = new LinkedHashMap<>();
            for (String u : users) allStats.put(u, null); // keys only – dropdown uses keySet()
            model.addAttribute("allStatistics", allStats);

            com.mtg.collection.dto.UserStatistics stats = statisticsService.getStatisticsForUser(user);
            model.addAttribute("userStatistics", stats);
            model.addAttribute("selectedUser", user);
        } else {
            // No user selected: compute full stats for all users (summary table)
            Map<String, com.mtg.collection.dto.UserStatistics> allStats = statisticsService.getStatisticsForAllUsers();
            model.addAttribute("allStatistics", allStats);
        }

        return "statistics";
    }
}